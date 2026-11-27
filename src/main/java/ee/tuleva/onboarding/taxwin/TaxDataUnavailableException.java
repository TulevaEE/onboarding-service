package ee.tuleva.onboarding.taxwin;

public class TaxDataUnavailableException extends RuntimeException {

  public TaxDataUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
