package ee.tuleva.onboarding.auth.event;

import ee.tuleva.onboarding.auth.AuthenticationAttributes;
import ee.tuleva.onboarding.auth.principal.Person;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

public abstract class AbstractTokenEvent extends ApplicationEvent {

  @Getter private final Person person;

  @Getter private final AuthenticationAttributes attributes;

  @Getter private final AuthorizationGrantType authorizationGrantType;

  public AbstractTokenEvent(
      Object source,
      Person person,
      AuthorizationGrantType authorizationGrantType,
      AuthenticationAttributes attributes) {
    super(source);
    this.authorizationGrantType = authorizationGrantType;
    this.person = person;
    this.attributes = attributes;
  }
}
