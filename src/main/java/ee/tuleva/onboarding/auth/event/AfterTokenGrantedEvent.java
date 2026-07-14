package ee.tuleva.onboarding.auth.event;

import ee.tuleva.onboarding.auth.AuthenticationTokens;
import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import lombok.Getter;
import org.jspecify.annotations.NullMarked;

@Getter
@NullMarked
public class AfterTokenGrantedEvent extends TokenGrantedEvent {

  private final AuthenticationTokens tokens;

  public AfterTokenGrantedEvent(
      Object source, AuthenticatedPerson person, GrantType grantType, AuthenticationTokens tokens) {
    super(source, person, grantType);
    this.tokens = tokens;
  }

  public String getAccessToken() {
    return tokens.accessToken();
  }
}
