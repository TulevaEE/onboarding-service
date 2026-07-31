package ee.tuleva.onboarding.aml.sanctions;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyb.CompanyDto;
import java.util.Set;

public interface PepAndSanctionCheckService {
  MatchResponse match(Person person, Set<Country> countries);

  MatchResponse matchCompany(CompanyDto company);
}
