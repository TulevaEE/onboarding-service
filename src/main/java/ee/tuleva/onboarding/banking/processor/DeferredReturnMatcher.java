package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_BOUNCE_BACK;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_CANCELLED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.*;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankMessagesProcessingCompleted;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.user.UserService;
import java.util.Set;
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
    var originalPaymentId = originalPayment.getId();

    if (originalPayment.getUserId() != null) {
      if (savingsFundLedger.hasLedgerEntry(originalPaymentId, PAYMENT_CANCELLED)) {
        return;
      }
      var user = userService.getByIdOrThrow(originalPayment.getUserId());
      log.info(
          "Deferred return matching: creating ledger entry for user-cancelled payment: paymentId={}, amount={}",
          originalPaymentId,
          originalPayment.getAmount());
      savingsFundLedger.recordPaymentCancelled(
          user, originalPayment.getAmount(), originalPaymentId);
    } else {
      if (savingsFundLedger.hasLedgerEntry(originalPaymentId, PAYMENT_BOUNCE_BACK)) {
        return;
      }
      log.info(
          "Deferred return matching: creating ledger entry for unattributed payment bounce back: paymentId={}, amount={}",
          originalPaymentId,
          originalPayment.getAmount());
      savingsFundLedger.bounceBackUnattributedPayment(
          originalPayment.getAmount(), originalPaymentId);
    }

    transitionToReturned(originalPayment);
  }

  private void transitionToReturned(SavingFundPayment originalPayment) {
    var status = originalPayment.getStatus();
    if (status == RETURNED) {
      return;
    }

    var paymentId = originalPayment.getId();

    if (status == VERIFIED) {
      if (originalPayment.getReturnReason() == null) {
        savingFundPaymentRepository.addReturnReason(paymentId, "Returned via deferred matching");
      }
      savingFundPaymentRepository.changeStatus(paymentId, TO_BE_RETURNED);
    } else if (status == RECEIVED) {
      savingFundPaymentRepository.changeStatus(paymentId, TO_BE_RETURNED);
    }

    if (Set.of(RECEIVED, VERIFIED, TO_BE_RETURNED).contains(status)) {
      savingFundPaymentRepository.changeStatus(paymentId, RETURNED);
      log.info(
          "Deferred return matching: transitioned original payment to RETURNED: paymentId={}, previousStatus={}",
          paymentId,
          status);
    }
  }
}
