package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.swedbank.statement.BankAccountType.WITHDRAWAL_EUR;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.nav.SavingsFundNavProvider;
import ee.tuleva.onboarding.swedbank.fetcher.SwedbankAccountConfiguration;
import ee.tuleva.onboarding.swedbank.http.SwedbankGatewayClient;
import ee.tuleva.onboarding.swedbank.payment.PaymentRequest;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("!staging")
public class RedemptionBatchJob {

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

  @Scheduled(cron = "0 0 16 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "RedemptionBatchJob", lockAtMostFor = "30m", lockAtLeastFor = "1m")
  public void processDailyRedemptions() {
    LocalDate today = LocalDate.now(clock);
    if (!publicHolidays.isWorkingDay(today)) {
      log.info("Skipping redemption processing: today is not a working day");
      return;
    }

    log.info("Starting daily redemption processing");

    Instant currentCutoff = getCurrentCutoff();
    Instant previousCutoff = getPreviousCutoff();

    reservePendingRequests(currentCutoff);
    processReservedRequests(previousCutoff);

    log.info("Completed daily redemption processing");
  }

  private void reservePendingRequests(Instant currentCutoff) {
    List<RedemptionRequest> toReserve =
        redemptionRequestRepository.findByStatusAndRequestedAtBeforeAndCancelledAtIsNull(
            PENDING, currentCutoff);
    log.info("Reserving {} pending requests submitted before {}", toReserve.size(), currentCutoff);

    for (RedemptionRequest request : toReserve) {
      try {
        transactionTemplate.executeWithoutResult(
            status -> {
              User user = userService.getByIdOrThrow(request.getUserId());
              savingsFundLedger.reserveFundUnitsForRedemption(user, request.getFundUnits());
              redemptionStatusService.changeStatus(request.getId(), RESERVED);
              log.info(
                  "Reserved redemption request: id={}, userId={}, fundUnits={}",
                  request.getId(),
                  request.getUserId(),
                  request.getFundUnits());
            });
      } catch (Exception e) {
        log.error("Failed to reserve redemption request: id={}", request.getId(), e);
        handleError(request.getId(), e);
      }
    }
  }

  private void processReservedRequests(Instant previousCutoff) {
    List<RedemptionRequest> toProcess =
        redemptionRequestRepository.findByStatusAndRequestedAtBefore(RESERVED, previousCutoff);

    if (toProcess.isEmpty()) {
      log.info("No reserved requests to process for cutoff {}", previousCutoff);
      return;
    }

    log.info(
        "Processing {} reserved requests submitted before {}", toProcess.size(), previousCutoff);

    BigDecimal nav = getNAV();
    BigDecimal totalCashAmount = ZERO;

    for (RedemptionRequest request : toProcess) {
      try {
        BigDecimal cashAmount =
            transactionTemplate.execute(
                status -> {
                  User user = userService.getByIdOrThrow(request.getUserId());
                  BigDecimal amount = request.getFundUnits().multiply(nav).setScale(2, HALF_UP);

                  RedemptionRequest toUpdate =
                      redemptionRequestRepository.findById(request.getId()).orElseThrow();
                  toUpdate.setCashAmount(amount);
                  toUpdate.setNavPerUnit(nav);
                  redemptionRequestRepository.save(toUpdate);

                  savingsFundLedger.redeemFundUnitsFromReserved(
                      user, request.getFundUnits(), amount, nav);

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

    transactionTemplate.executeWithoutResult(
        status -> {
          savingsFundLedger.transferFromFundAccount(totalAmount);

          UUID batchId = UUID.randomUUID();
          PaymentRequest paymentRequest =
              PaymentRequest.tulevaPaymentBuilder(batchId)
                  .remitterIban(swedbankAccountConfiguration.getAccountIban(FUND_INVESTMENT_EUR))
                  .beneficiaryName("Tuleva Fondid AS")
                  .beneficiaryIban(swedbankAccountConfiguration.getAccountIban(WITHDRAWAL_EUR))
                  .amount(totalAmount)
                  .description("Redemptions batch")
                  .build();

          swedbankGatewayClient.sendPaymentRequest(paymentRequest, batchId);
          log.info("Sent batch transfer request: batchId={}, amount={}", batchId, totalAmount);
        });
  }

  private void processIndividualPayouts(List<RedemptionRequest> requests) {
    for (RedemptionRequest request : requests) {
      RedemptionRequest updated =
          redemptionRequestRepository.findById(request.getId()).orElseThrow();
      if (updated.getCashAmount() == null) {
        continue;
      }

      try {
        transactionTemplate.executeWithoutResult(
            status -> {
              User user = userService.getByIdOrThrow(updated.getUserId());

              savingsFundLedger.recordRedemptionPayout(
                  user, updated.getCashAmount(), updated.getCustomerIban());

              PaymentRequest paymentRequest =
                  PaymentRequest.tulevaPaymentBuilder(updated.getId())
                      .remitterIban(swedbankAccountConfiguration.getAccountIban(WITHDRAWAL_EUR))
                      .beneficiaryName(user.getFirstName() + " " + user.getLastName())
                      .beneficiaryIban(updated.getCustomerIban())
                      .amount(updated.getCashAmount())
                      .description("Fondi tagasivõtmine")
                      .build();

              swedbankGatewayClient.sendPaymentRequest(paymentRequest, UUID.randomUUID());

              redemptionStatusService.changeStatus(updated.getId(), PAID_OUT);

              updated.setProcessedAt(Instant.now());
              redemptionRequestRepository.save(updated);

              log.info(
                  "Processed individual payout: id={}, amount={}, iban={}",
                  updated.getId(),
                  updated.getCashAmount(),
                  updated.getCustomerIban());
            });
      } catch (Exception e) {
        log.error("Failed to process payout for redemption: id={}", updated.getId(), e);
        handleError(updated.getId(), e);
      }
    }
  }

  private void handleError(UUID requestId, Exception e) {
    try {
      RedemptionRequest request = redemptionRequestRepository.findById(requestId).orElseThrow();
      request.setErrorReason(e.getMessage());
      redemptionRequestRepository.save(request);
      redemptionStatusService.changeStatus(requestId, FAILED);
    } catch (Exception ex) {
      log.error("Failed to mark redemption as failed: id={}", requestId, ex);
    }
  }

  private Instant getCurrentCutoff() {
    var now = clock.instant().atZone(CUTOFF_TIMEZONE);
    var today = now.toLocalDate();

    if (publicHolidays.isWorkingDay(today) && now.getHour() >= 16) {
      return today.atTime(16, 0).atZone(CUTOFF_TIMEZONE).toInstant();
    }

    return publicHolidays
        .previousWorkingDay(today)
        .atTime(16, 0)
        .atZone(CUTOFF_TIMEZONE)
        .toInstant();
  }

  private Instant getPreviousCutoff() {
    var currentCutoff = getCurrentCutoff().atZone(CUTOFF_TIMEZONE).toLocalDate();
    return publicHolidays
        .previousWorkingDay(currentCutoff)
        .atTime(16, 0)
        .atZone(CUTOFF_TIMEZONE)
        .toInstant();
  }

  private BigDecimal getNAV() {
    return navProvider.getCurrentNav();
  }
}
