package ee.tuleva.onboarding.auth.event;

import ee.tuleva.onboarding.auth.principal.Person;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AfterTokenGrantedEvent extends ApplicationEvent {

  private final Person person;
  private final String jwtToken;

  public AfterTokenGrantedEvent(Object source, Person person, String jwtToken) {
    super(source);
    this.person = person;
    this.jwtToken = jwtToken;
  }
}
