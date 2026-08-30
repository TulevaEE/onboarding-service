package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Country;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public interface UserContacts {

  ContactSummary forPerson(Person person);

  ContactSummary forPerson(Person person, String jwtToken);

  ContactSummary update(
      Person person,
      @Nullable String email,
      @Nullable String phoneNumber,
      @Nullable Country address);

  record ContactSummary(
      @Nullable String email,
      @Nullable String phoneNumber,
      String pensionAccountNumber,
      String country,
      String activeSecondPillarFundPik,
      boolean secondPillarActive,
      boolean thirdPillarActive,
      Instant secondPillarOpenDate,
      Instant thirdPillarInitDate,
      @Nullable Instant lastUpdateDate) {}
}
