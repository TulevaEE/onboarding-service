package ee.tuleva.onboarding.nudge;

import ee.tuleva.onboarding.auth.principal.Person;

@FunctionalInterface
public interface SecondPillarEarlyWithdrawals {

  boolean hasCompleted(Person person);
}
