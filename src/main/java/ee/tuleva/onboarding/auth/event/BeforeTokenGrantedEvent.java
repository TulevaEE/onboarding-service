package ee.tuleva.onboarding.auth.event;

import ee.tuleva.onboarding.auth.GrantType;
import ee.tuleva.onboarding.auth.principal.Person;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.springframework.security.oauth2.provider.OAuth2Authentication;

@Getter
public class BeforeTokenGrantedEvent extends ApplicationEvent {

  private final Person person;
  private final OAuth2Authentication authentication;
  private final GrantType grantType;

  public BeforeTokenGrantedEvent(
      Object source, Person person, OAuth2Authentication authentication, GrantType grantType) {
    super(source);
    this.person = person;
    this.authentication = authentication;
    this.grantType = grantType;
  }
}
