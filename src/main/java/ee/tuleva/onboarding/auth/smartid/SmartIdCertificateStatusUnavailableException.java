package ee.tuleva.onboarding.auth.smartid;

public class SmartIdCertificateStatusUnavailableException extends RuntimeException {
  public SmartIdCertificateStatusUnavailableException(String reason) {
    super("Smart-ID authentication certificate status unavailable: reason=" + reason);
  }

  public SmartIdCertificateStatusUnavailableException(String reason, Throwable cause) {
    super("Smart-ID authentication certificate status unavailable: reason=" + reason, cause);
  }
}
