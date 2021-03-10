package ee.tuleva.onboarding.auth.response;

import org.springframework.security.oauth2.common.exceptions.ClientAuthenticationException;

public class AuthNotCompleteException extends ClientAuthenticationException {
  public AuthNotCompleteException() {
    super("Please keep polling.");
  }

  @Override
  public String getOAuth2ErrorCode() {
    return "AUTHENTICATION_NOT_COMPLETE";
  }

  @Override
  public int getHttpErrorCode() {
    return 200; // TODO: change to something else
  }
}
