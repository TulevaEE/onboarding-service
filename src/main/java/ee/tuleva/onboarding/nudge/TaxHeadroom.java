package ee.tuleva.onboarding.nudge;

import ee.tuleva.onboarding.auth.principal.Person;

@FunctionalInterface
public interface TaxHeadroom {

  boolean hasHeadroom(Person person);
}
