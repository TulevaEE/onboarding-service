package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.auth.principal.Person;

public interface SecondPillarExits {

  SecondPillarExit of(Person person);
}
