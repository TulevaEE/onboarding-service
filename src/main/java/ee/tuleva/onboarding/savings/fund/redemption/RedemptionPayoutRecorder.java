package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.HOLD;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.DUPLICATE_PAYOUT;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PAYOUT_WITHOUT_REQUEST;

import ee.tuleva.onboarding.banking.check.payment.PaymentCheckService;
import ee.tuleva.onboarding.banking.payment.EndToEndIdConverter;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.LedgerRefs;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedemptionPayoutRecorder {

  private final SavingsFundLedger savingsFundLedger;
  private final RedemptionRequestRepository redemptionRequestRepository;
  private final RedemptionStatusService redemptionStatusService;
  private final EndToEndIdConverter endToEndIdConverter;
  private final PaymentCheckService paymentCheckService;

  public void recordOutgoingPayout(SavingFundPayment payment) {
    Optional<RedemptionRequest> request =
        findRedemptionRequestByEndToEndId(payment.getEndToEndId());
    if (request.isEmpty()) {
      log.error(
          "No matching RedemptionRequest found for outgoing payment: endToEndId={}, beneficiaryIban={}, amount={}",
          payment.getEndToEndId(),
          payment.getBeneficiaryIban(),
          payment.getAmount());
      // Money left the payout account and we cannot say who authorised it. Until now this
      // reached Sentry only, where it looks like any other stack trace.
      paymentCheckService.record(
          PAYOUT_WITHOUT_REQUEST,
          HOLD,
          String.valueOf(payment.getEndToEndId()),
          "a debit from the payout account matches no redemption request");
      return;
    }
    processRedemptionPayout(request.get(), payment);
  }

  private void processRedemptionPayout(RedemptionRequest request, SavingFundPayment payment) {
    if (savingsFundLedger.hasPayoutEntry(request.getId())) {
      log.error(
          "Ledger payout entry already exists but status is REDEEMED: id={}", request.getId());
      paymentCheckService.record(
          DUPLICATE_PAYOUT,
          HOLD,
          request.getId().toString(),
          "a payout entry already exists for this redemption, so it looks paid twice");
    } else {
      var party = LedgerRefs.from(request.getPartyId());
      var amount = payment.getAmount().negate();
      log.info(
          "Creating ledger entry for redemption payout: redemptionId={}, amount={}",
          request.getId(),
          amount);
      savingsFundLedger.recordRedemptionPayout(
          party, amount, request.getCustomerIban(), request.getId(), payment.bookingDateOrThrow());
    }
    markRedemptionAsProcessed(request);
  }

  private Optional<RedemptionRequest> findRedemptionRequestByEndToEndId(
      @Nullable String endToEndId) {
    return endToEndIdConverter
        .toUuid(endToEndId)
        .flatMap(
            id ->
                redemptionRequestRepository.findByIdAndStatus(
                    id, RedemptionRequest.Status.REDEEMED));
  }

  private void markRedemptionAsProcessed(RedemptionRequest request) {
    log.info("Marking redemption as PROCESSED: id={}", request.getId());
    redemptionStatusService.changeStatus(request.getId(), RedemptionRequest.Status.PROCESSED);
  }
}
