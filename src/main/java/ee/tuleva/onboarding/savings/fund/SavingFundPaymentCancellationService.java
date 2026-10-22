package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.PaymentReturningService.MAX_RETURN_REASON_LENGTH;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.SavingFundDeadlinesService;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SavingFundPaymentCancellationService {

  static final String OWNER_CANCELLATION_RETURN_REASON = "Kasutaja soovil";

  private final SavingFundPaymentRepository repository;
  private final SavingFundDeadlinesService savingFundDeadlinesService;
  private final Clock clock;

  @Transactional
  public void cancelPayment(PartyId partyId, UUID paymentId) {
    var payment = findPayment(paymentId);
    if (!partyId.equals(payment.getPartyId()) || payment.getCancelledAt() != null) {
      throw new NoSuchElementException();
    }
    cancelBeforeDeadline(payment, OWNER_CANCELLATION_RETURN_REASON);
  }

  @Transactional
  public SavingFundPayment cancelPayment(UUID paymentId, String reason) {
    var validReason = validateCancellationReason(paymentId, reason);
    var payment = findPayment(paymentId);
    if (payment.getPartyId() == null) {
      throw new PaymentCancellationRefusedException(
          "Cannot cancel unattributed payment: paymentId=" + paymentId);
    }
    if (payment.getCancelledAt() != null) {
      throw new PaymentCancellationRefusedException(
          "Payment already cancelled: paymentId=" + paymentId);
    }
    cancelBeforeDeadline(payment, validReason);
    return findPayment(paymentId);
  }

  private String validateCancellationReason(UUID paymentId, String reason) {
    var stripped = reason.strip();
    if (stripped.isEmpty()) {
      throw new IllegalArgumentException("Cancellation reason is blank: paymentId=" + paymentId);
    }
    if (stripped.length() > MAX_RETURN_REASON_LENGTH) {
      throw new IllegalArgumentException(
          "Cancellation reason too long: paymentId="
              + paymentId
              + ", length="
              + stripped.length()
              + ", maxLength="
              + MAX_RETURN_REASON_LENGTH);
    }
    if (stripped.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException(
          "Cancellation reason contains control characters: paymentId=" + paymentId);
    }
    return stripped;
  }

  private SavingFundPayment findPayment(UUID paymentId) {
    return repository
        .findById(paymentId)
        .orElseThrow(() -> new NoSuchElementException("Payment not found: paymentId=" + paymentId));
  }

  private void cancelBeforeDeadline(SavingFundPayment payment, String reason) {
    var deadline = savingFundDeadlinesService.getCancellationDeadline(payment);
    if (deadline.isBefore(clock.instant())) {
      throw new PaymentCancellationRefusedException(
          "Payment cancellation deadline has passed: paymentId="
              + payment.getId()
              + ", deadline="
              + deadline);
    }
    repository.cancel(payment.getId(), reason);
  }
}
