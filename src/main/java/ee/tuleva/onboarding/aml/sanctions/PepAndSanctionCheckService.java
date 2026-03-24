package ee.tuleva.onboarding.aml.sanctions;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyb.CompanyDto;

public interface PepAndSanctionCheckService {
  MatchResponse match(Person person, Country country);

  MatchResponse matchCompany(CompanyDto company);
}
