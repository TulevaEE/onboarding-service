package ee.tuleva.onboarding.withdrawals;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.user.personalcode.PersonalCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@NullMarked
public class WithdrawalEligibilityService {
  private final EpisService episService;
  private final Clock estonianClock;

  public WithdrawalEligibilityDto getWithdrawalEligibility(Person person) {
    var fundPensionCalculation = episService.getFundPensionCalculation(person);
    var earlyRetirementDate = PersonalCode.getEarlyRetirementDate(person.getPersonalCode());
    var reducedTaxAvailableFrom =
        canWithdrawThirdPillarWithReducedTaxFrom(person, earlyRetirementDate);

    return WithdrawalEligibilityDto.builder()
        .hasReachedEarlyRetirementAge(!today().isBefore(earlyRetirementDate))
        .canWithdrawThirdPillarWithReducedTax(
            reducedTaxAvailableFrom != null && !today().isBefore(reducedTaxAvailableFrom))
        .canWithdrawThirdPillarWithReducedTaxFrom(reducedTaxAvailableFrom)
        .earlyRetirementDate(earlyRetirementDate)
        .age(PersonalCode.getAge(person.getPersonalCode()))
        .recommendedDurationYears(fundPensionCalculation.durationYears())
        .arrestsOrBankruptciesPresent(getArrestsOrBankruptciesPresent(person))
        .build();
  }

  private LocalDate today() {
    return LocalDate.now(estonianClock);
  }

  private boolean getArrestsOrBankruptciesPresent(Person person) {
    var arrestsBankruptcies = episService.getArrestsBankruptciesPresent(person);
    return arrestsBankruptcies.activeBankruptciesPresent()
        || arrestsBankruptcies.activeArrestsPresent();
  }

  private @Nullable LocalDate canWithdrawThirdPillarWithReducedTaxFrom(
      Person person, LocalDate earlyRetirementDate) {
    var contactDetails = episService.getContactDetails(person);
    var thirdPillarInitDate = contactDetails.getThirdPillarInitDate();

    if (thirdPillarInitDate == null || !contactDetails.isThirdPillarActive()) {
      return null;
    }

    var thirdPillar2021Deadline = ZonedDateTime.parse("2021-01-01T00:00:00+02:00").toInstant();
    var thirdPillarOpenedBefore2021 = thirdPillarInitDate.isBefore(thirdPillar2021Deadline);

    var reducedTaxAgeReachedOn =
        thirdPillarOpenedBefore2021
            ? PersonalCode.getDateOfBirth(person.getPersonalCode()).plusYears(55)
            : earlyRetirementDate;

    var thirdPillarHeldForFiveYearsOn =
        LocalDate.ofInstant(thirdPillarInitDate, estonianClock.getZone()).plusYears(5);

    return reducedTaxAgeReachedOn.isAfter(thirdPillarHeldForFiveYearsOn)
        ? reducedTaxAgeReachedOn
        : thirdPillarHeldForFiveYearsOn;
  }
}
