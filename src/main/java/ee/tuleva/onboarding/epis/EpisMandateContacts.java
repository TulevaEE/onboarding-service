package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.mandate.MandateContactDetails;
import ee.tuleva.onboarding.mandate.MandateContacts;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class EpisMandateContacts implements MandateContacts {

  private final ContactDetailsService contactDetailsService;

  @Override
  public MandateContactDetails getContactDetails(Person person) {
    return ContactDetailsMapper.toMandateContactDetails(
        contactDetailsService.getContactDetails(person));
  }

  @Override
  public void updateContactDetails(
      Person person,
      @Nullable String email,
      @Nullable String phoneNumber,
      @Nullable Country address) {
    contactDetailsService.updateContactDetails(person, email, phoneNumber, address);
  }

  @Override
  public void clearCache(Person person) {
    contactDetailsService.clearCache(person);
  }
}
