package ee.tuleva.onboarding.auth.idcard.exception;

public class UnknownCountryException extends RuntimeException {
  public UnknownCountryException(String country) {
    super("Unsupported ID-card country: country=" + country);
  }
}
