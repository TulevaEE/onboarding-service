package ee.tuleva.onboarding.banking.payment;

/** Thrown when a second submission is attempted for an endToEndId we have already used. */
public class OutgoingPaymentBlockedException extends RuntimeException {

  public OutgoingPaymentBlockedException(String endToEndId, OutgoingPaymentStatus priorStatus) {
    super(
        "Refusing to resend a payment we have already submitted: endToEndId=%s, priorStatus=%s"
            .formatted(endToEndId, priorStatus));
  }
}
