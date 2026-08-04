package ee.tuleva.onboarding.kyc;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Country;
import java.util.Set;

public record BeforeKycCheckedEvent(Person person, Set<Country> countries) {}
