package ee.tuleva.onboarding.taxwin;

public class InvalidTaxDataConsentException extends RuntimeException {

  public InvalidTaxDataConsentException(String message, Throwable cause) {
    super(message, cause);
  }
}
