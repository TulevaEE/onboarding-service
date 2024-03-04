package ee.tuleva.onboarding.auth.event;

import ee.tuleva.onboarding.auth.AccessAndRefreshToken;
import ee.tuleva.onboarding.auth.principal.Person;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AfterTokenGrantedEvent extends ApplicationEvent {

  private final Person person;
  private final AccessAndRefreshToken tokens;

  public AfterTokenGrantedEvent(Object source, Person person, AccessAndRefreshToken tokens) {
    super(source);
    this.person = person;
    this.tokens = tokens;
  }
}
