package ee.tuleva.onboarding.withdrawals;

import static ee.tuleva.onboarding.time.ClockHolder.clock;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import java.time.ZonedDateTime;
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
    boolean hasReachedEarlyRetirementAge = hasReachedEarlyRetirementAge(person);
    boolean canWithdrawFromThirdPillarWithReducedTax = canWithdrawEarlyFromThirdPillar(person);

    return new WithdrawalEligibilityDto(
        hasReachedEarlyRetirementAge,
        canWithdrawFromThirdPillarWithReducedTax,
        PersonalCode.getAge(person.getPersonalCode()),
        fundPensionCalculation.durationYears(),
        getArrestsOrBankruptciesPresent(person));
  }

  private boolean hasReachedEarlyRetirementAge(Person person) {
    return PersonalCode.getAge(person.getPersonalCode())
        >= PersonalCode.getEarlyRetirementAge(person.getPersonalCode());
  }

  private boolean getArrestsOrBankruptciesPresent(Person person) {
    var arrestsBankruptcies = episService.getArrestsBankruptciesPresent(person);
    return arrestsBankruptcies.activeBankruptciesPresent()
        || arrestsBankruptcies.activeArrestsPresent();
  }

  private boolean canWithdrawEarlyFromThirdPillar(Person person) {
    if (hasReachedEarlyRetirementAge(person)) {
      return true;
    }

    var contactDetails = episService.getContactDetails(person);

    var ageAtLeast55 = PersonalCode.getAge(person.getPersonalCode()) >= 55;

    var fiveYearsAgo = ZonedDateTime.now(clock().getZone()).minusYears(5).toInstant();
    var thirdPillarOpenedAtLeast5YearsAgo =
        contactDetails.isThirdPillarActive()
            && contactDetails.getThirdPillarInitDate().isBefore(fiveYearsAgo);

    var thirdPillar2021Deadline = ZonedDateTime.parse("2021-01-01T00:00:00+02:00").toInstant();
    var thirdPillarOpenedBefore2021 =
        contactDetails.isThirdPillarActive()
            && contactDetails.getThirdPillarInitDate().isBefore(thirdPillar2021Deadline);

    return ageAtLeast55 && thirdPillarOpenedBefore2021 && thirdPillarOpenedAtLeast5YearsAgo;
  }
}
