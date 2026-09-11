package ee.tuleva.onboarding.auth.smartid;

public class SmartIdCallbackRejectedException extends RuntimeException {
  public SmartIdCallbackRejectedException(String message) {
    super(message);
  }
}
