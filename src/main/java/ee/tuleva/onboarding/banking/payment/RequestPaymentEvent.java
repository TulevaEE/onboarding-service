package ee.tuleva.onboarding.banking.payment;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * @param sourceId the redemption request, saving fund payment or batch this payment came from
 * @param batchId shared by a redemption transfer and the payouts it funds, so the two can be tied
 *     together in the outgoing payment log
 */
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
