package ee.tuleva.onboarding.taxwin;

public class TaxDataRequestRejectedException extends RuntimeException {

  public TaxDataRequestRejectedException(String message, Throwable cause) {
    super(message, cause);
  }
}
