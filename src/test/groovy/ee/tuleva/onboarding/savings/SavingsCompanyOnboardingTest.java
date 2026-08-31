package ee.tuleva.onboarding.savings;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.kyb.CompanyOnboarding;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsCompanyOnboardingTest {

  @Mock SavingsFundOnboardingService onboardingService;
  @InjectMocks SavingsCompanyOnboarding companyOnboarding;

  @ParameterizedTest
  @CsvSource({"PENDING,PENDING", "REJECTED,REJECTED", "COMPLETED,COMPLETED"})
  void mapsEveryOnboardingStatusToTheKybState(
      SavingsFundOnboardingStatus status, CompanyOnboarding.State expected) {
    given(onboardingService.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.of(status));

    assertThat(companyOnboarding.findState("12345678")).contains(expected);
  }

  @Test
  void emptyWhenTheCompanyHasNoOnboardingRecord() {
    given(onboardingService.findStatus("12345678", LEGAL_ENTITY)).willReturn(Optional.empty());

    assertThat(companyOnboarding.findState("12345678")).isEmpty();
  }
}
