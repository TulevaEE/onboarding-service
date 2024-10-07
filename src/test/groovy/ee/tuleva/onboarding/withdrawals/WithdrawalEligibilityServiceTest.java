package ee.tuleva.onboarding.withdrawals;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.auth.PersonFixture.sampleRetirementAgePerson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.epis.EpisService;
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

    var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

    assertThat(result.hasReachedEarlyRetirementAge()).isTrue();
    assertThat(result.recommendedDurationYears()).isEqualTo(30);
  }

  @Test
  @DisplayName("calculates withdrawal eligibility for those under 60")
  void shouldCalculateWithdrawalEligibilityForThoseUnder60() {
    var aPerson = samplePerson;

    when(episService.getFundPensionCalculation(aPerson))
        .thenReturn(new FundPensionCalculationDto(0));

    var result = withdrawalEligibilityService.getWithdrawalEligibility(aPerson);

    assertThat(result.hasReachedEarlyRetirementAge()).isFalse();
    assertThat(result.recommendedDurationYears()).isEqualTo(0);
  }
}
