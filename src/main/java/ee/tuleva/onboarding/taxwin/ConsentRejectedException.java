package ee.tuleva.onboarding.taxwin;

public class ConsentRejectedException extends RuntimeException {

  public ConsentRejectedException(String message) {
    super(message);
  }

  public ConsentRejectedException(String message, Throwable cause) {
    super(message, cause);
  }
}
