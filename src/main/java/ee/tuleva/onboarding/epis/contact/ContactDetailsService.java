package ee.tuleva.onboarding.epis.contact;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.event.ContactDetailsUpdatedEvent;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.address.Address;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactDetailsService {

  private final EpisService episService;
  private final ApplicationEventPublisher eventPublisher;

  public UserPreferences updateContactDetails(User user, Address address) {
    UserPreferences contactDetails = episService.getContactDetails(user);
    contactDetails.setEmail(user.getEmail());
    contactDetails.setPhoneNumber(user.getPhoneNumber());
    contactDetails.setAddress(address);
    UserPreferences updatedContactDetails = episService.updateContactDetails(user, contactDetails);
    eventPublisher.publishEvent(new ContactDetailsUpdatedEvent(this, user, updatedContactDetails));
    return updatedContactDetails;
  }

  public UserPreferences getContactDetails(Person person, String token) {
    return episService.getContactDetails(person, token);
  }
}
