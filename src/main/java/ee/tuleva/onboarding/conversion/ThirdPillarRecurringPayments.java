package ee.tuleva.onboarding.conversion;

import ee.tuleva.onboarding.auth.principal.Person;

public interface ThirdPillarRecurringPayments {

  boolean detectedFor(Person person);
}
