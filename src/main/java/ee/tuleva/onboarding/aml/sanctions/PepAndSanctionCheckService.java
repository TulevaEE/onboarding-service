package ee.tuleva.onboarding.aml.sanctions;

import ee.tuleva.onboarding.auth.principal.Person;

public interface PepAndSanctionCheckService {
  MatchResponse match(Person person, String country);
}
