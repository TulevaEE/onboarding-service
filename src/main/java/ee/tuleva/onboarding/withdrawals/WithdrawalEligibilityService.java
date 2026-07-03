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

    return WithdrawalEligibilityDto.builder()
        .hasReachedEarlyRetirementAge(hasReachedEarlyRetirementAge(person))
        .canWithdrawThirdPillarWithReducedTax(canWithdrawThirdPillarWithReducedTax(person))
        .age(PersonalCode.getAge(person.getPersonalCode()))
        .recommendedDurationYears(fundPensionCalculation.durationYears())
        .arrestsOrBankruptciesPresent(getArrestsOrBankruptciesPresent(person))
        .build();
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

  private boolean canWithdrawThirdPillarWithReducedTax(Person person) {
    var contactDetails = episService.getContactDetails(person);
    var thirdPillarInitDate = contactDetails.getThirdPillarInitDate();

    if (thirdPillarInitDate == null || !contactDetails.isThirdPillarActive()) {
      return false;
    }

    var fiveYearsAgo = ZonedDateTime.now(clock()).minusYears(5).toInstant();
    var thirdPillarHeldForFiveYears = thirdPillarInitDate.isBefore(fiveYearsAgo);

    var thirdPillar2021Deadline = ZonedDateTime.parse("2021-01-01T00:00:00+02:00").toInstant();
    var thirdPillarOpenedBefore2021 = thirdPillarInitDate.isBefore(thirdPillar2021Deadline);

    var reducedTaxAge =
        thirdPillarOpenedBefore2021
            ? 55
            : PersonalCode.getEarlyRetirementAge(person.getPersonalCode());
    var hasReachedReducedTaxAge = PersonalCode.getAge(person.getPersonalCode()) >= reducedTaxAge;

    return hasReachedReducedTaxAge && thirdPillarHeldForFiveYears;
  }
}
