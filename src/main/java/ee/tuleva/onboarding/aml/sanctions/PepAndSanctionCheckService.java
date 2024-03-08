package ee.tuleva.onboarding.aml.sanctions;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.user.address.Address;

public interface PepAndSanctionCheckService {
  MatchResponse match(Person person, Address address);
}
