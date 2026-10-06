package ee.tuleva.onboarding.auth.browser;

public class PushLoginStartedTooSoonException extends RuntimeException {
  public PushLoginStartedTooSoonException(PushLogin pushLogin) {
    super(
        "Push login started too soon after the previous one from this browser: pushLogin="
            + pushLogin);
  }
}
