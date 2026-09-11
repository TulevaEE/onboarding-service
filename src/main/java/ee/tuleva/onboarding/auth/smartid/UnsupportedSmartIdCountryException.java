package ee.tuleva.onboarding.auth.smartid;

public class UnsupportedSmartIdCountryException extends RuntimeException {
  public UnsupportedSmartIdCountryException(String country) {
    super("Unsupported Smart-ID account country: country=" + country);
  }
}
