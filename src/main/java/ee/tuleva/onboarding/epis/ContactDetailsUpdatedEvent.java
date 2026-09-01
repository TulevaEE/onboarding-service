package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.auth.principal.Person;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ContactDetailsUpdatedEvent extends ApplicationEvent {

  private final Person person;
  private final ContactDetails contactDetails;

  public ContactDetailsUpdatedEvent(Object source, Person person, ContactDetails contactDetails) {
    super(source);
    this.person = person;
    this.contactDetails = contactDetails;
  }
}
