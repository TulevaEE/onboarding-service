package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_BOUNCE_BACK;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.PAYMENT_RECEIVED;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNATTRIBUTED_PAYMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNATTRIBUTED_PAYMENT_RECONCILED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RETURNED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;

import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.PartyId;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UnattributedPaymentAttributionService {

  private final SavingFundPaymentRepository paymentRepository;
  private final SavingsFundLedger savingsFundLedger;
  private final SavingsFundOnboardingService savingsFundOnboardingService;

  @Transactional
  public SavingFundPayment attribute(UUID paymentId, PartyId partyId, boolean returnCancelled) {
    var payment =
        paymentRepository
            .findById(paymentId)
            .orElseThrow(
                () -> new IllegalArgumentException("Payment not found: paymentId=" + paymentId));

    if (!Set.of(TO_BE_RETURNED, RETURNED).contains(payment.getStatus())) {
      throw new IllegalStateException(
          "Payment is not attributable: paymentId="
              + paymentId
              + ", status="
              + payment.getStatus());
    }

    if (payment.getStatus() == RETURNED && !returnCancelled) {
      throw new IllegalStateException(
          "Outbound return may still be in flight for RETURNED payment; cancel the pending bank"
              + " return first, then retry with returnCancelled=true: paymentId="
              + paymentId);
    }
    if (payment.getAmount().signum() <= 0) {
      throw new IllegalStateException(
          "Only incoming payments can be attributed: paymentId="
              + paymentId
              + ", amount="
              + payment.getAmount());
    }
    if (!savingsFundLedger.hasLedgerEntry(paymentId, UNATTRIBUTED_PAYMENT)) {
      throw new IllegalStateException(
          "Payment has no unattributed ledger entry: paymentId=" + paymentId);
    }
    if (savingsFundLedger.hasLedgerEntry(paymentId, PAYMENT_BOUNCE_BACK)
        || savingsFundLedger.hasLedgerEntry(paymentId, PAYMENT_RECEIVED)
        || savingsFundLedger.hasLedgerEntry(paymentId, UNATTRIBUTED_PAYMENT_RECONCILED)) {
      throw new IllegalStateException(
          "Payment is already attributed or returned: paymentId=" + paymentId);
    }
    if (!savingsFundOnboardingService.isOnboardingCompleted(partyId)) {
      throw new IllegalStateException(
          "Party has not completed savings fund onboarding: partyType="
              + partyId.type()
              + ", partyCode="
              + partyId.code());
    }

    log.info(
        "Manually attributing unattributed payment: paymentId={}, amount={}, partyType={}, partyCode={}",
        paymentId,
        payment.getAmount(),
        partyId.type(),
        partyId.code());

    var bookingDate = payment.bookingDate();
    if (bookingDate != null) {
      savingsFundLedger.reconcileUnattributedPayment(
          partyId, payment.getAmount(), paymentId, bookingDate);
    } else {
      savingsFundLedger.reconcileUnattributedPayment(partyId, payment.getAmount(), paymentId);
    }
    paymentRepository.attributeManually(paymentId, partyId, returnCancelled);

    return paymentRepository.findById(paymentId).orElseThrow();
  }
}
