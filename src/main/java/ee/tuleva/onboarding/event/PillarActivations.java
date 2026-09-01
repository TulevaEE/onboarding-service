package ee.tuleva.onboarding.event;

import ee.tuleva.onboarding.auth.principal.Person;

@FunctionalInterface
public interface PillarActivations {

  PillarActivation forPerson(Person person);
}
