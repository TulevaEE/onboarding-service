package ee.tuleva.onboarding.epis.contact.event;

import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ContactDetailsUpdatedEvent extends ApplicationEvent {

  private final User user;
  private final UserPreferences contactDetails;

  public ContactDetailsUpdatedEvent(Object source, User user, UserPreferences contactDetails) {
    super(source);
    this.user = user;
    this.contactDetails = contactDetails;
  }
}
