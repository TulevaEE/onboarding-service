package ee.tuleva.onboarding.auth.ocsp;

public class CertificateStatusUnavailableException extends RuntimeException {
  public CertificateStatusUnavailableException(String reason) {
    super("Authentication certificate status unavailable: reason=" + reason);
  }

  public CertificateStatusUnavailableException(String reason, Throwable cause) {
    super("Authentication certificate status unavailable: reason=" + reason, cause);
  }
}
