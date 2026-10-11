package ee.tuleva.onboarding.auth.smartid;

public class SmartIdCertificateRevokedException extends RuntimeException {
  public SmartIdCertificateRevokedException(String status) {
    super("Smart-ID authentication certificate is not good: status=" + status);
  }
}
