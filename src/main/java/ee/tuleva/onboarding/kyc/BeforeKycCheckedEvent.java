package ee.tuleva.onboarding.kyc;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.user.address.Address;

public record BeforeKycCheckedEvent(Person person, Address address) {}
