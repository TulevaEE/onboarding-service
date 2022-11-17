package ee.tuleva.onboarding.auth.response;

import java.io.Serial;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

public class AuthNotCompleteException extends OAuth2AuthenticationException {

  @Serial private static final long serialVersionUID = -7114237655758069952L;

  public AuthNotCompleteException() {
    super(new OAuth2Error("AUTHENTICATION_NOT_COMPLETE"), "Please keep polling.");
  }
}
