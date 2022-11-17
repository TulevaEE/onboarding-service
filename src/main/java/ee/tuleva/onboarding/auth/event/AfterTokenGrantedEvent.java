package ee.tuleva.onboarding.auth.event;

import ee.tuleva.onboarding.auth.AuthenticationAttributes;
import ee.tuleva.onboarding.auth.principal.Person;
import java.io.Serial;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

public class AfterTokenGrantedEvent extends AbstractTokenEvent {

  @Serial private static final long serialVersionUID = -1119089469249229456L;

  public AfterTokenGrantedEvent(
      Object source,
      Person person,
      AuthenticationAttributes attributes,
      AuthorizationGrantType authorizationGrantType) {
    super(source, person, authorizationGrantType, attributes);
  }
}
