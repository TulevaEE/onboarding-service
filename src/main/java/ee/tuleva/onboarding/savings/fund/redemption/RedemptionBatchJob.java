package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.nav.SavingsFundNavProvider;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.payment.EndToEndIdConverter;
import ee.tuleva.onboarding.swedbank.payment.PaymentRequest;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("!staging")
public class RedemptionBatchJob {

  private static final LocalTime CUTOFF_TIME = LocalTime.of(16, 0, 0);
  private static final ZoneId CUTOFF_TIMEZONE = ZoneId.of("Europe/Tallinn");

  private final Clock clock;
  private final PublicHolidays publicHolidays;
  private final RedemptionRequestRepository redemptionRequestRepository;
  private final RedemptionStatusService redemptionStatusService;
  private final SavingsFundLedger savingsFundLedger;
  private final UserService userService;
  private final SwedbankGatewayClient swedbankGatewayClient;
  private final SwedbankAccountConfiguration swedbankAccountConfiguration;
  private final TransactionTemplate transactionTemplate;
  private final SavingsFundNavProvider navProvider;
  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final EndToEndIdConverter endToEndIdConverter;

  // @Scheduled(fixedRateString = "1m")
  @SchedulerLock(name = "RedemptionBatchJob", lockAtMostFor = "50s", lockAtLeastFor = "10s")
  public void runJob() {
    Instant cutoff = getCutoffForProcessing();
    List<RedemptionRequest> toProcess =
        redemptionRequestRepository.findByStatusAndRequestedAtBefore(VERIFIED, cutoff);

    if (toProcess.isEmpty()) {
      return;
    }

    log.info(
        "Running redemption job for {} verified requests submitted before {}",
        toProcess.size(),
        cutoff);
    processVerifiedRequests(toProcess);
  }

  private Instant getCutoffForProcessing() {
    var todaysCutoff = getCutoff(LocalDate.now(clock));
    var currentTime = clock.instant();
    var isTodayWorkingDay =
        publicHolidays.isWorkingDay(currentTime.atZone(CUTOFF_TIMEZONE).toLocalDate());

    if (currentTime.isBefore(todaysCutoff) || !isTodayWorkingDay) {
      return getSecondToLastWorkingDayCutoff();
    }
    return getLastWorkingDayCutoff();
  }

  private Instant getSecondToLastWorkingDayCutoff() {
    var lastWorkingDay = publicHolidays.previousWorkingDay(LocalDate.now(clock));
    var secondToLastWorkingDay = publicHolidays.previousWorkingDay(lastWorkingDay);
    return getCutoff(secondToLastWorkingDay);
  }

  private Instant getLastWorkingDayCutoff() {
    var lastWorkingDay = publicHolidays.previousWorkingDay(LocalDate.now(clock));
    return getCutoff(lastWorkingDay);
  }

  private Instant getCutoff(LocalDate date) {
    return ZonedDateTime.of(date, CUTOFF_TIME, CUTOFF_TIMEZONE).toInstant();
  }

  private void processVerifiedRequests(List<RedemptionRequest> toProcess) {
    BigDecimal nav = getNAV();
    BigDecimal totalCashAmount = ZERO;

    for (RedemptionRequest request : toProcess) {
      try {
        BigDecimal cashAmount =
            transactionTemplate.execute(
                ignored -> {
                  RedemptionRequest toUpdate =
                      redemptionRequestRepository.findById(request.getId()).orElseThrow();

                  if (toUpdate.getCashAmount() != null) {
                    log.info(
                        "Skipping pricing for already priced redemption: id={}, cashAmount={}",
                        request.getId(),
                        toUpdate.getCashAmount());
                    return toUpdate.getCashAmount();
                  }

                  if (savingsFundLedger.hasPricingEntry(request.getId())) {
                    log.warn(
                        "Ledger entry already exists for redemption pricing: id={}",
                        request.getId());
                    return ZERO;
                  }

                  User user = userService.getByIdOrThrow(request.getUserId());
                  BigDecimal amount = request.getFundUnits().multiply(nav).setScale(2, HALF_UP);

                  toUpdate.setCashAmount(amount);
                  toUpdate.setNavPerUnit(nav);
                  redemptionRequestRepository.save(toUpdate);

                  savingsFundLedger.redeemFundUnitsFromReserved(
                      user, request.getFundUnits(), amount, nav, request.getId());

                  log.info(
                      "Priced redemption request: id={}, fundUnits={}, cashAmount={}, nav={}",
                      request.getId(),
                      request.getFundUnits(),
                      amount,
                      nav);
                  return amount;
                });
        totalCashAmount = totalCashAmount.add(cashAmount);
      } catch (Exception e) {
        log.error("Failed to price redemption request: id={}", request.getId(), e);
        handleError(request.getId(), e);
      }
    }

    if (totalCashAmount.compareTo(ZERO) > 0) {
      transferFromFundAccount(totalCashAmount);
      processIndividualPayouts(toProcess);
    }
  }

  private void transferFromFundAccount(BigDecimal totalAmount) {
    log.info("Transferring {} EUR from fund account to payout account", totalAmount);

    UUID batchId = UUID.randomUUID();
    PaymentRequest paymentRequest =
        PaymentRequest.tulevaPaymentBuilder(endToEndIdConverter.toEndToEndId(batchId))
            .remitterIban(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
            .beneficiaryName("Tuleva Fondid AS")
            .beneficiaryIban(swedbankAccountConfiguration.getAccountIban(WITHDRAWAL_EUR))
            .amount(totalAmount)
            .description("Redemptions batch")
            .build();

    swedbankGatewayClient.sendPaymentRequest(paymentRequest, batchId);
    log.info("Sent batch transfer request: batchId={}, amount={}", batchId, totalAmount);
  }

  private void processIndividualPayouts(List<RedemptionRequest> requests) {
    for (RedemptionRequest request : requests) {
      RedemptionRequest updated =
          redemptionRequestRepository.findById(request.getId()).orElseThrow();
      if (updated.getCashAmount() == null) {
        continue;
      }

      try {
        String beneficiaryName = getBeneficiaryName(updated.getUserId(), updated.getCustomerIban());

        PaymentRequest paymentRequest =
            PaymentRequest.tulevaPaymentBuilder(endToEndIdConverter.toEndToEndId(updated.getId()))
                .remitterIban(swedbankAccountConfiguration.getAccountIban(WITHDRAWAL_EUR))
                .beneficiaryName(beneficiaryName)
                .beneficiaryIban(updated.getCustomerIban())
                .amount(updated.getCashAmount())
                .description("Fondi tagasivõtmine")
                .build();

        swedbankGatewayClient.sendPaymentRequest(paymentRequest, updated.getId());

        markAsRedeemed(updated.getId());

        log.info(
            "Processed individual payout: id={}, amount={}, iban={}, beneficiaryName={}",
            updated.getId(),
            updated.getCashAmount(),
            updated.getCustomerIban(),
            beneficiaryName);
      } catch (Exception e) {
        log.error("Failed to process payout for redemption: id={}", updated.getId(), e);
        handleError(updated.getId(), e);
      }
    }
  }

  private void markAsRedeemed(UUID requestId) {
    redemptionStatusService.changeStatus(requestId, REDEEMED);
    RedemptionRequest request = redemptionRequestRepository.findById(requestId).orElseThrow();
    request.setProcessedAt(Instant.now(clock));
    redemptionRequestRepository.save(request);
  }

  private String getBeneficiaryName(Long userId, String iban) {
    return savingFundPaymentRepository
        .findRemitterNameByIban(userId, iban)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "IBAN not found in user's deposit accounts: userId="
                        + userId
                        + ", iban="
                        + iban));
  }

  private void handleError(UUID requestId, Exception e) {
    try {
      RedemptionRequest request = redemptionRequestRepository.findById(requestId).orElseThrow();
      request.setErrorReason(e.toString());
      redemptionRequestRepository.save(request);
      redemptionStatusService.changeStatus(requestId, FAILED);
    } catch (Exception ex) {
      log.error("Failed to mark redemption as failed: id={}", requestId, ex);
    }
  }

  private BigDecimal getNAV() {
    return navProvider.getCurrentNav();
  }
}
