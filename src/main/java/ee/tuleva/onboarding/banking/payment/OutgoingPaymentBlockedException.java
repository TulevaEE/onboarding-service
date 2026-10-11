package ee.tuleva.onboarding.banking.payment;

public class OutgoingPaymentBlockedException extends RuntimeException {
  public OutgoingPaymentBlockedException(String endToEndId, OutgoingPaymentStatus priorStatus) {
    super(
        "Refusing to resend a payment we have already submitted: endToEndId=%s, priorStatus=%s"
            .formatted(endToEndId, priorStatus));
  }
}
