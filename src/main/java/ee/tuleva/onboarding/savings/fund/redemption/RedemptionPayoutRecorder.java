package ee.tuleva.onboarding.savings.fund.redemption;

import ee.tuleva.onboarding.banking.payment.EndToEndIdConverter;
import ee.tuleva.onboarding.ledger.LedgerParty.PartyType;
import ee.tuleva.onboarding.ledger.PartyRef;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.user.UserService;
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
  private final UserService userService;
  private final RedemptionRequestRepository redemptionRequestRepository;
  private final RedemptionStatusService redemptionStatusService;
  private final EndToEndIdConverter endToEndIdConverter;

  public void recordOutgoingPayout(SavingFundPayment payment) {
    Optional<RedemptionRequest> request =
        findRedemptionRequestByEndToEndId(payment.getEndToEndId());
    if (request.isEmpty()) {
      log.error(
          "No matching RedemptionRequest found for outgoing payment: endToEndId={}, beneficiaryIban={}, amount={}",
          payment.getEndToEndId(),
          payment.getBeneficiaryIban(),
          payment.getAmount());
      return;
    }
    processRedemptionPayout(request.get(), payment);
  }

  private void processRedemptionPayout(RedemptionRequest request, SavingFundPayment payment) {
    if (savingsFundLedger.hasPayoutEntry(request.getId())) {
      log.error(
          "Ledger payout entry already exists but status is REDEEMED: id={}", request.getId());
    } else {
      var user = userService.getByIdOrThrow(request.getUserId());
      var party = new PartyRef(PartyType.PERSON, user.getPersonalCode());
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
