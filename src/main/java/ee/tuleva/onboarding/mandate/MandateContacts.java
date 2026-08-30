package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Country;
import org.jspecify.annotations.Nullable;

public interface MandateContacts {

  MandateContactDetails getContactDetails(Person person);

  void updateContactDetails(
      Person person,
      @Nullable String email,
      @Nullable String phoneNumber,
      @Nullable Country address);

  void clearCache(Person person);
}
