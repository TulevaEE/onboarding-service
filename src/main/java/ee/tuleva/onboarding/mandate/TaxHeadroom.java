package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.auth.principal.Person;

@FunctionalInterface
public interface TaxHeadroom {

  boolean hasHeadroom(Person person);
}
