package ee.tuleva.onboarding.auth.partner;

public class UnverifiableHandoverTokenException extends RuntimeException {

  public UnverifiableHandoverTokenException(String message) {
    super(message);
  }
}
