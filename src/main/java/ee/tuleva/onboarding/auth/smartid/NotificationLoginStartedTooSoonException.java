package ee.tuleva.onboarding.auth.smartid;

public class NotificationLoginStartedTooSoonException extends RuntimeException {
  public NotificationLoginStartedTooSoonException() {
    super("Push login started too soon after the previous one from this browser.");
  }
}
