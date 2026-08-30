package ee.tuleva.onboarding.withdrawals;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.mandate.WithdrawalReadiness;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class WithdrawalReadinessAdapter implements WithdrawalReadiness {

  private final WithdrawalEligibilityService withdrawalEligibilityService;

  @Override
  public Readiness forPerson(Person person) {
    var eligibility = withdrawalEligibilityService.getWithdrawalEligibility(person);
    return new Readiness(
        eligibility.canWithdrawThirdPillarWithReducedTax(),
        eligibility.hasReachedEarlyRetirementAge());
  }
}
