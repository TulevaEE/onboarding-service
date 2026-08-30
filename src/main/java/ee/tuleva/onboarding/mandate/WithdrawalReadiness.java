package ee.tuleva.onboarding.mandate;

import ee.tuleva.onboarding.auth.principal.Person;

@FunctionalInterface
public interface WithdrawalReadiness {

  Readiness forPerson(Person person);

  record Readiness(
      boolean canWithdrawThirdPillarWithReducedTax, boolean hasReachedEarlyRetirementAge) {}
}
