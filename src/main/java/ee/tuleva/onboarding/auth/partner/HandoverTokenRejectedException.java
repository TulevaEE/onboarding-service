package ee.tuleva.onboarding.auth.partner;

public class HandoverTokenRejectedException extends RuntimeException {

  public HandoverTokenRejectedException(String message) {
    super(message);
  }
}
