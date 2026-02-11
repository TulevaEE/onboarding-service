package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_BOUNCE_BACK;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_CANCELLED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.*;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.BankMessagesProcessingCompleted;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.notification.DeferredReturnMatchingCompletedEvent;
import ee.tuleva.onboarding.user.UserService;
import java.math.BigDecimal;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
  private final ApplicationEventPublisher eventPublisher;

  @EventListener
  @Transactional
  public void onBankMessagesProcessed(BankMessagesProcessingCompleted event) {
    var unmatchedReturns =
        savingFundPaymentRepository.findUnmatchedOutgoingReturns().stream()
            .filter(returnPayment -> !hasReturnLedgerEntry(returnPayment))
            .toList();

    if (unmatchedReturns.isEmpty()) {
      return;
    }

    log.info(
        "Deferred return matching: found {} unmatched outgoing returns", unmatchedReturns.size());

    var matchedCount = 0;
    var unmatchedCount = 0;
    var totalAmount = ZERO;

    for (SavingFundPayment returnPayment : unmatchedReturns) {
      var amount = matchReturn(returnPayment);
      if (amount != null) {
        matchedCount++;
        totalAmount = totalAmount.add(amount);
      } else {
        unmatchedCount++;
      }
    }

    log.info(
        "Deferred return matching completed: matchedCount={}, unmatchedCount={}, totalAmount={}",
        matchedCount,
        unmatchedCount,
        totalAmount);

    if (matchedCount > 0) {
      eventPublisher.publishEvent(
          new DeferredReturnMatchingCompletedEvent(matchedCount, unmatchedCount, totalAmount));
    }
  }

  private boolean hasReturnLedgerEntry(SavingFundPayment returnPayment) {
    return savingFundPaymentRepository
        .findOriginalPaymentForReturn(returnPayment.getEndToEndId())
        .map(
            original ->
                savingsFundLedger.hasLedgerEntry(original.getId(), PAYMENT_BOUNCE_BACK)
                    || savingsFundLedger.hasLedgerEntry(original.getId(), PAYMENT_CANCELLED))
        .orElse(false);
  }

  private BigDecimal matchReturn(SavingFundPayment returnPayment) {
    var original =
        savingFundPaymentRepository
            .findOriginalPaymentForReturn(returnPayment.getEndToEndId())
            .or(
                () ->
                    savingFundPaymentRepository.findOriginalPaymentByIbanAndAmount(
                        returnPayment.getBeneficiaryIban(), returnPayment.getAmount()));

    if (original.isPresent()) {
      completePaymentReturn(original.get());
      return original.get().getAmount();
    }

    log.warn(
        "Deferred return matching: original payment not found: endToEndId={}, beneficiaryIban={}, amount={}",
        returnPayment.getEndToEndId(),
        returnPayment.getBeneficiaryIban(),
        returnPayment.getAmount());
    return null;
  }

  private void completePaymentReturn(SavingFundPayment originalPayment) {
    var originalPaymentId = originalPayment.getId();

    if (savingsFundLedger.hasLedgerEntry(originalPaymentId, PAYMENT_BOUNCE_BACK)
        || savingsFundLedger.hasLedgerEntry(originalPaymentId, PAYMENT_CANCELLED)) {
      return;
    }

    if (originalPayment.getUserId() != null) {
      var user = userService.getByIdOrThrow(originalPayment.getUserId());
      log.info(
          "Deferred return matching: creating ledger entry for user-cancelled payment: paymentId={}, amount={}",
          originalPaymentId,
          originalPayment.getAmount());
      savingsFundLedger.recordPaymentCancelled(
          user, originalPayment.getAmount(), originalPaymentId);
    } else {
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
