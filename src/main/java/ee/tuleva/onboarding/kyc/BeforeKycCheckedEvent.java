package ee.tuleva.onboarding.kyc;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Country;

public record BeforeKycCheckedEvent(Person person, Country country) {}
