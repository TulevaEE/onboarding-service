package ee.tuleva.onboarding.auth.principal;

public class MinorCannotSelfAuthenticateException extends RuntimeException {

  public MinorCannotSelfAuthenticateException(String personalCode) {
    super("Minor cannot self-authenticate: personalCode=" + personalCode);
  }
}
