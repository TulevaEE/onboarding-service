package ee.tuleva.onboarding.auth.event;

import ee.tuleva.onboarding.auth.AuthenticationTokens;
import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import lombok.Getter;

@Getter
public class AfterTokenGrantedEvent extends BeforeTokenGrantedEvent {

  private final AuthenticatedPerson person;
  private final AuthenticationTokens tokens;

  public AfterTokenGrantedEvent(
      Object source, AuthenticatedPerson person, GrantType grantType, AuthenticationTokens tokens) {
    super(source, person, grantType);
    this.person = person;
    this.tokens = tokens;
  }

  public String getAccessToken() {
    return tokens.accessToken();
  }
}
