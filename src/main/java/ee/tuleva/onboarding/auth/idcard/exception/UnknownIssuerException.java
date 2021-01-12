package ee.tuleva.onboarding.auth.idcard.exception;

public class UnknownIssuerException extends RuntimeException {
  public UnknownIssuerException(String issuer) {
    super(issuer);
  }
}
