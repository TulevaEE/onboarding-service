package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.payment.EndToEndIdConverter;
import ee.tuleva.onboarding.banking.payment.PaymentRequest;
import ee.tuleva.onboarding.banking.payment.RequestPaymentEvent;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.FundNavProvider;
import ee.tuleva.onboarding.savings.fund.LedgerRefs;
import ee.tuleva.onboarding.savings.fund.notification.RedemptionBatchCompletedEvent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("!staging")
public class RedemptionBatchJob {

  private static final ZoneId CUTOFF_TIMEZONE = RedemptionCutoff.TALLINN;

  private final Clock clock;
  private final PublicHolidays publicHolidays;
  private final RedemptionRequestRepository redemptionRequestRepository;
  private final RedemptionStatusService redemptionStatusService;
  private final SavingsFundLedger savingsFundLedger;
  private final ApplicationEventPublisher eventPublisher;
  private final BankAccounts bankAccounts;
  private final TransactionTemplate transactionTemplate;
  private final FundNavProvider navProvider;
  private final EndToEndIdConverter endToEndIdConverter;
  private final RedemptionPayoutService payoutService;
  private final RedemptionHoldNotifier holdNotifier;

  @Scheduled(fixedRateString = "1m")
  @SchedulerLock(name = "RedemptionBatchJob", lockAtMostFor = "30m", lockAtLeastFor = "10s")
  public void runJob() {
    Instant cutoff = getCutoffForProcessing();
    List<RedemptionRequest> toProcess =
        redemptionRequestRepository.findAcceptedBefore(VERIFIED, cutoff);

    if (toProcess.isEmpty()) {
      return;
    }

    log.info(
        "Running redemption job for {} verified requests submitted before {}",
        toProcess.size(),
        cutoff);

    processVerifiedRequests(toProcess, dealingDate(cutoff));
  }

  private Instant getCutoffForProcessing() {
    var today = todayInTallinn();
    var todaysCutoff = getCutoff(today);
    var currentTime = clock.instant();
    var isTodayWorkingDay = publicHolidays.isWorkingDay(today);

    if (currentTime.isBefore(todaysCutoff) || !isTodayWorkingDay) {
      return getSecondToLastWorkingDayCutoff();
    }
    return getLastWorkingDayCutoff();
  }

  private Instant getSecondToLastWorkingDayCutoff() {
    var lastWorkingDay = publicHolidays.previousWorkingDay(todayInTallinn());
    var secondToLastWorkingDay = publicHolidays.previousWorkingDay(lastWorkingDay);
    return getCutoff(secondToLastWorkingDay);
  }

  private Instant getLastWorkingDayCutoff() {
    var lastWorkingDay = publicHolidays.previousWorkingDay(todayInTallinn());
    return getCutoff(lastWorkingDay);
  }

  private LocalDate todayInTallinn() {
    return clock.instant().atZone(CUTOFF_TIMEZONE).toLocalDate();
  }

  private LocalDate dealingDate(Instant cutoff) {
    return cutoff.atZone(CUTOFF_TIMEZONE).toLocalDate();
  }

  private Instant getCutoff(LocalDate date) {
    return RedemptionCutoff.cutoffInstant(date);
  }

  private void processVerifiedRequests(List<RedemptionRequest> toProcess, LocalDate dealingDate) {
    BigDecimal nav = getNAV(dealingDate);
    BigDecimal totalCashAmount = ZERO;

    for (RedemptionRequest request : toProcess) {
      List<RedemptionRequest> held = new ArrayList<>();
      try {
        BigDecimal cashAmount =
            transactionTemplate.execute(
                ignored -> {
                  RedemptionRequest toUpdate =
                      redemptionRequestRepository.findByIdForUpdate(request.getId()).orElseThrow();

                  if (toUpdate.getCashAmount() != null) {
                    log.info(
                        "Skipping pricing for already priced redemption: id={}, cashAmount={}",
                        request.getId(),
                        toUpdate.getCashAmount());
                    holdPayoutIfFlagged(toUpdate, held);
                    return toUpdate.getCashAmount();
                  }

                  if (savingsFundLedger.hasPricingEntry(request.getId())) {
                    log.warn(
                        "Ledger entry already exists for redemption pricing: id={}",
                        request.getId());
                    return ZERO;
                  }

                  PartyId party = toUpdate.getPartyId();
                  BigDecimal amount = request.getFundUnits().multiply(nav).setScale(2, HALF_UP);
                  toUpdate.setCashAmount(amount);
                  toUpdate.setNavPerUnit(nav);
                  redemptionRequestRepository.save(toUpdate);

                  savingsFundLedger.redeemFundUnitsFromReserved(
                      LedgerRefs.from(party), request.getFundUnits(), amount, nav, request.getId());

                  log.info(
                      "Priced redemption request: id={}, fundUnits={}, cashAmount={}, nav={}",
                      request.getId(),
                      request.getFundUnits(),
                      amount,
                      nav);
                  holdPayoutIfFlagged(toUpdate, held);
                  return amount;
                });
        totalCashAmount = totalCashAmount.add(cashAmount);
      } catch (Exception e) {
        log.error("Failed to price redemption request: id={}", request.getId(), e);
        payoutService.markAsFailed(request.getId(), e);
      }
      held.forEach(holdNotifier::notifyPayoutHeldAtPricing);
    }

    if (totalCashAmount.compareTo(ZERO) > 0) {
      // Held payouts are in the total: their cash waits on the withdrawal account until released.
      transferFromFundAccount(totalCashAmount);
      PayoutResult result = processIndividualPayouts(toProcess);
      eventPublisher.publishEvent(
          new RedemptionBatchCompletedEvent(
              toProcess.size(), result.payoutCount(), result.heldCount(), totalCashAmount, nav));
    }
  }

  private void transferFromFundAccount(BigDecimal totalAmount) {
    log.info("Transferring {} EUR from fund account to payout account", totalAmount);

    UUID batchId = UUID.randomUUID();
    PaymentRequest paymentRequest =
        PaymentRequest.tulevaPaymentBuilder(endToEndIdConverter.toEndToEndId(batchId))
            .remitterIban(bankAccounts.getIban(TKF100, FUND_INVESTMENT_EUR))
            .beneficiaryName("Tuleva Täiendav Kogumisfond")
            .beneficiaryIban(bankAccounts.getIban(TKF100, WITHDRAWAL_EUR))
            .amount(totalAmount)
            .description("Redemptions batch")
            .build();

    eventPublisher.publishEvent(new RequestPaymentEvent(paymentRequest, batchId));
    log.info("Sent batch transfer request: batchId={}, amount={}", batchId, totalAmount);
  }

  private PayoutResult processIndividualPayouts(List<RedemptionRequest> requests) {
    int payoutCount = 0;
    int heldCount = 0;
    for (RedemptionRequest request : requests) {
      RedemptionRequest updated =
          redemptionRequestRepository.findById(request.getId()).orElseThrow();

      if (updated.getCashAmount() == null) {
        continue;
      }

      if (updated.hasActiveHold()) {
        heldCount++;
        continue;
      }

      try {
        payoutService.payOut(updated);
        payoutCount++;
      } catch (Exception e) {
        log.error("Failed to process payout for redemption: id={}", updated.getId(), e);
        payoutService.markAsFailed(updated.getId(), e);
      }
    }
    return new PayoutResult(payoutCount, heldCount);
  }

  // Runs inside the pricing transaction with the row locked: a priced request under AML hold is
  // PAYOUT_HELD in the same commit, so it can neither be paid nor priced twice.
  private void holdPayoutIfFlagged(RedemptionRequest request, List<RedemptionRequest> held) {
    if (request.hasActiveHold() && request.getStatus() == VERIFIED) {
      redemptionStatusService.changeStatus(request.getId(), PAYOUT_HELD);
      held.add(request);
      log.info(
          "Held payout of redemption under AML review: id={}, cashAmount={}, reason={}",
          request.getId(),
          request.getCashAmount(),
          request.getHoldReason());
    }
  }

  @Transactional
  public void retryFailedPayout(UUID requestId) {
    RedemptionRequest request =
        redemptionRequestRepository
            .findByIdForUpdate(requestId)
            .orElseThrow(
                () -> new NoSuchElementException("Redemption request not found: id=" + requestId));

    if (request.getStatus() != FAILED) {
      throw new IllegalStateException(
          "Cannot retry payout: id=" + requestId + ", status=" + request.getStatus());
    }
    if (request.getCashAmount() == null) {
      throw new IllegalStateException("Cannot retry payout, not priced: id=" + requestId);
    }
    if (request.hasActiveHold()) {
      throw new IllegalStateException(
          "Cannot retry payout, redemption is on AML hold, release it first: id=" + requestId);
    }

    request.setErrorReason(null);
    redemptionRequestRepository.save(request);
    payoutService.payOut(request);

    log.info("Retried failed payout: id={}, amount={}", request.getId(), request.getCashAmount());
  }

  private BigDecimal getNAV(LocalDate dealingDate) {
    return navProvider.getVerifiedNavForIssuingAndRedeeming(TKF100, dealingDate);
  }

  private record PayoutResult(int payoutCount, int heldCount) {}
}
