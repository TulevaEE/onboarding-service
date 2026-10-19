package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.error.ErrorsResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactDetailsService {

  private final EpisService episService;
  private final ApplicationEventPublisher eventPublisher;

  public ContactDetails updateContactDetails(
      Person person,
      @Nullable String email,
      @Nullable String phoneNumber,
      @Nullable Country address) {
    ContactDetails contactDetails = episService.getContactDetails(person);
    contactDetails.setEmail(email);
    contactDetails.setPhoneNumber(phoneNumber);
    if (address != null) {
      contactDetails.setAddress(address);
    }
    ContactDetails updatedContactDetails = null;
    try {
      updatedContactDetails = episService.updateContactDetails(person, contactDetails);
    } catch (ErrorsResponseException e) {
      updatedContactDetails = contactDetails;
      log.error("Contact details update failed: personalCode={}", person.getPersonalCode(), e);
    }
    eventPublisher.publishEvent(
        new ContactDetailsUpdatedEvent(this, person, updatedContactDetails));
    return updatedContactDetails;
  }

  public void clearCache(Person person) {
    episService.clearCache(person);
  }

  public ContactDetails getContactDetails(Person person, String jwtToken) {
    return episService.getContactDetails(person, jwtToken);
  }

  public ContactDetails getContactDetails(Person person) {
    return episService.getContactDetails(person);
  }
}
