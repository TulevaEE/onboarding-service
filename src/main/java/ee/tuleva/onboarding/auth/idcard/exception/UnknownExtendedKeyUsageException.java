package ee.tuleva.onboarding.auth.idcard.exception;

public class UnknownExtendedKeyUsageException extends RuntimeException {
  public UnknownExtendedKeyUsageException(String message) {
    super(message);
  }

  public UnknownExtendedKeyUsageException() {
    super();
  }
}
