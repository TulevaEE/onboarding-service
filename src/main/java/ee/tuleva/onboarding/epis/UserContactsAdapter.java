package ee.tuleva.onboarding.epis;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.user.UserContacts;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class UserContactsAdapter implements UserContacts {

  private final ContactDetailsService contactDetailsService;

  @Override
  public ContactSummary forPerson(Person person) {
    return toContactSummary(contactDetailsService.getContactDetails(person));
  }

  @Override
  public ContactSummary forPerson(Person person, String jwtToken) {
    return toContactSummary(contactDetailsService.getContactDetails(person, jwtToken));
  }

  @Override
  public ContactSummary update(
      Person person,
      @Nullable String email,
      @Nullable String phoneNumber,
      @Nullable Country address) {
    return toContactSummary(
        contactDetailsService.updateContactDetails(person, email, phoneNumber, address));
  }

  private static ContactSummary toContactSummary(ContactDetails contactDetails) {
    return new ContactSummary(
        contactDetails.getEmail(),
        contactDetails.getPhoneNumber(),
        contactDetails.getPensionAccountNumber(),
        contactDetails.getCountry(),
        contactDetails.getActiveSecondPillarFundPik(),
        contactDetails.isSecondPillarActive(),
        contactDetails.isThirdPillarActive(),
        contactDetails.getSecondPillarOpenDate(),
        contactDetails.getThirdPillarInitDate(),
        contactDetails.getLastUpdateDate());
  }
}
