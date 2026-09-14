package ee.tuleva.onboarding.banking.payment;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record RequestPaymentEvent(
    PaymentRequest paymentRequest,
    UUID sourceId,
    OutgoingPaymentType paymentType,
    @Nullable UUID batchId) {
  public RequestPaymentEvent(
      PaymentRequest paymentRequest, UUID sourceId, OutgoingPaymentType paymentType) {
    this(paymentRequest, sourceId, paymentType, null);
  }
}
