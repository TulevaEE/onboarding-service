package ee.tuleva.onboarding.savings.fund;

public class PaymentCancellationRefusedException extends RuntimeException {

  public PaymentCancellationRefusedException(String message) {
    super(message);
  }
}
