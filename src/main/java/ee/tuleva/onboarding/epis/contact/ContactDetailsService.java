package ee.tuleva.onboarding.epis.contact;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.event.ContactDetailsUpdatedEvent;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.user.User;
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

  public ContactDetails updateContactDetails(User user, Country address) {
    ContactDetails contactDetails = episService.getContactDetails(user);
    contactDetails.setEmail(user.getEmail());
    contactDetails.setPhoneNumber(user.getPhoneNumber());
    contactDetails.setAddress(address);
    ContactDetails updatedContactDetails = null;
    try {
      updatedContactDetails = episService.updateContactDetails(user, contactDetails);
    } catch (ErrorsResponseException e) {
      updatedContactDetails = contactDetails;
      log.error("Contact details update failed for user " + user.getId(), e);
    }
    eventPublisher.publishEvent(new ContactDetailsUpdatedEvent(this, user, updatedContactDetails));
    return updatedContactDetails;
  }

  public ContactDetails getContactDetails(Person person, String jwtToken) {
    return episService.getContactDetails(person, jwtToken);
  }

  public ContactDetails getContactDetails(Person person) {
    return episService.getContactDetails(person);
  }
}
