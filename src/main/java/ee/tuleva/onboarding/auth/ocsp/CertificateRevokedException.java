package ee.tuleva.onboarding.auth.ocsp;

public class CertificateRevokedException extends RuntimeException {
  public CertificateRevokedException(String status) {
    super("Authentication certificate is not good: status=" + status);
  }
}
