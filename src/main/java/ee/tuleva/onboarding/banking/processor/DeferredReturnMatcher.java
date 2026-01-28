package ee.tuleva.onboarding.banking.processor;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankMessagesProcessingCompleted;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeferredReturnMatcher {

  private final SavingFundPaymentRepository savingFundPaymentRepository;
  private final SavingsFundLedger savingsFundLedger;
  private final UserService userService;

  @EventListener
  @Transactional
  public void onBankMessagesProcessed(BankMessagesProcessingCompleted event) {
    var unmatchedReturns = savingFundPaymentRepository.findUnmatchedOutgoingReturns();

    if (unmatchedReturns.isEmpty()) {
      return;
    }

    log.info(
        "Deferred return matching: found {} unmatched outgoing returns", unmatchedReturns.size());

    for (SavingFundPayment returnPayment : unmatchedReturns) {
      matchReturn(returnPayment);
    }

    log.info(
        "Deferred return matching: completed processing of unmatched returns: processedReturns={}",
        unmatchedReturns.size());
  }

  private void matchReturn(SavingFundPayment returnPayment) {
    savingFundPaymentRepository
        .findOriginalPaymentForReturn(returnPayment.getEndToEndId())
        .or(
            () ->
                savingFundPaymentRepository.findOriginalPaymentByIbanAndAmount(
                    returnPayment.getBeneficiaryIban(), returnPayment.getAmount()))
        .ifPresentOrElse(
            this::completePaymentReturn,
            () ->
                log.warn(
                    "Deferred return matching: original payment not found: endToEndId={}, beneficiaryIban={}, amount={}",
                    returnPayment.getEndToEndId(),
                    returnPayment.getBeneficiaryIban(),
                    returnPayment.getAmount()));
  }

  private void completePaymentReturn(SavingFundPayment originalPayment) {
    if (savingsFundLedger.hasLedgerEntry(originalPayment.getId())) {
      return;
    }

    if (originalPayment.getUserId() != null) {
      var user = userService.getByIdOrThrow(originalPayment.getUserId());
      log.info(
          "Deferred return matching: creating ledger entry for user-cancelled payment: paymentId={}, amount={}",
          originalPayment.getId(),
          originalPayment.getAmount());
      savingsFundLedger.recordPaymentCancelled(
          user, originalPayment.getAmount(), originalPayment.getId());
    } else {
      log.info(
          "Deferred return matching: creating ledger entry for unattributed payment bounce back: paymentId={}, amount={}",
          originalPayment.getId(),
          originalPayment.getAmount());
      savingsFundLedger.bounceBackUnattributedPayment(
          originalPayment.getAmount(), originalPayment.getId());
    }
  }
}
