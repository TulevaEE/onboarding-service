package ee.tuleva.onboarding.withdrawals;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class WithdrawalEligibilityService {
  private final EpisService episService;

  public WithdrawalEligibilityDto getWithdrawalEligibility(Person person) {
    var fundPensionCalculation = episService.getFundPensionCalculation(person);
    var arrestsBankruptcies = episService.getArrestsBankruptciesPresent(person);

    return new WithdrawalEligibilityDto(
        hasReachedRetirementAge(person),
        PersonalCode.getAge(person.getPersonalCode()),
        fundPensionCalculation.durationYears(),
        arrestsBankruptcies.activeBankruptciesPresent()
            || arrestsBankruptcies.activeArrestsPresent());
  }

  private boolean hasReachedRetirementAge(Person person) {
    return PersonalCode.getAge(person.getPersonalCode())
        >= PersonalCode.getEarlyRetirementAge(person.getPersonalCode());
  }
}
