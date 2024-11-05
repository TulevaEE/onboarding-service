package ee.tuleva.onboarding.withdrawals;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.auth.PersonFixture.sampleRetirementAgePerson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.withdrawals.ArrestsBankruptciesDto;
import ee.tuleva.onboarding.epis.withdrawals.FundPensionCalculationDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawalEligibilityServiceTest {
  @Mock private EpisService episService;

  @InjectMocks private WithdrawalEligibilityService withdrawalEligibilityService;

  @Test
  @DisplayName("calculates withdrawal eligibility for those over 60")
  void shouldCalculateWithdrawalEligibilityForThoseOver60() {
    var aPerson = sampleRetirementAgePerson;

    when(episService.getFundPensionCalculation(aPerson))
        .thenReturn(new FundPensionCalculationDto(30));

    when(episService.getArrestsBankruptciesPresent(aPerson))
        .thenReturn(new ArrestsBankruptciesDto(false, false));

    var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

    assertThat(result.hasReachedEarlyRetirementAge()).isTrue();
    assertThat(result.recommendedDurationYears()).isEqualTo(30);
    assertThat(result.age()).isNotNull();
    assertThat(result.arrestsOrBankruptciesPresent()).isFalse();
  }

  @Test
  @DisplayName("calculates withdrawal eligibility for those under 60")
  void shouldCalculateWithdrawalEligibilityForThoseUnder60() {
    var aPerson = samplePerson;

    when(episService.getFundPensionCalculation(aPerson))
        .thenReturn(new FundPensionCalculationDto(0));

    when(episService.getArrestsBankruptciesPresent(aPerson))
        .thenReturn(new ArrestsBankruptciesDto(false, false));

    var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

    assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
    assertThat(result.recommendedDurationYears()).isEqualTo(0);
    assertThat(result.age()).isNotNull();
  }

  @Test
  @DisplayName("calculates withdrawal eligibility for those under 60 with arrests")
  void shouldCalculateWithdrawalEligibilityForThoseUnder60WithArrest() {
    var aPerson = samplePerson;

    when(episService.getFundPensionCalculation(aPerson))
        .thenReturn(new FundPensionCalculationDto(0));

    when(episService.getArrestsBankruptciesPresent(aPerson))
        .thenReturn(new ArrestsBankruptciesDto(true, false));

    var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

    assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
    assertThat(result.recommendedDurationYears()).isEqualTo(0);
    assertThat(result.age()).isNotNull();
    assertThat(result.arrestsOrBankruptciesPresent()).isTrue();
  }

  @Test
  @DisplayName("calculates withdrawal eligibility for those under 60 with bankruptcies")
  void shouldCalculateWithdrawalEligibilityForThoseUnder60WithBankruptcy() {
    var aPerson = samplePerson;

    when(episService.getFundPensionCalculation(aPerson))
        .thenReturn(new FundPensionCalculationDto(0));

    when(episService.getArrestsBankruptciesPresent(aPerson))
        .thenReturn(new ArrestsBankruptciesDto(false, true));

    var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

    assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
    assertThat(result.recommendedDurationYears()).isEqualTo(0);
    assertThat(result.age()).isNotNull();
    assertThat(result.arrestsOrBankruptciesPresent()).isTrue();
  }
}
