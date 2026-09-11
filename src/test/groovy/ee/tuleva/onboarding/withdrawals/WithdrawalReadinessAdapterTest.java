package ee.tuleva.onboarding.withdrawals;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.mandate.WithdrawalReadiness;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawalReadinessAdapterTest {

  @Mock WithdrawalEligibilityService withdrawalEligibilityService;
  @InjectMocks WithdrawalReadinessAdapter adapter;

  @Test
  void mapsEligibilityToReadiness() {
    Person person = samplePerson();
    var eligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(true)
            .canWithdrawThirdPillarWithReducedTax(false)
            .earlyRetirementDate(LocalDate.of(2020, 1, 1))
            .age(66)
            .recommendedDurationYears(20)
            .arrestsOrBankruptciesPresent(false)
            .build();
    given(withdrawalEligibilityService.getWithdrawalEligibility(person)).willReturn(eligibility);

    assertThat(adapter.forPerson(person)).isEqualTo(new WithdrawalReadiness.Readiness(false, true));
  }
}
