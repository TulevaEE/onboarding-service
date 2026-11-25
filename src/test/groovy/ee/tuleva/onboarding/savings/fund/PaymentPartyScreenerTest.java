package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.aml.ScreeningOutcome.CLEAR;
import static ee.tuleva.onboarding.aml.ScreeningOutcome.SANCTION_HIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.aml.SanctionAndPepScreener;
import ee.tuleva.onboarding.kyb.CompanySanctionCheck;
import ee.tuleva.onboarding.savings.fund.PaymentAcceptance.Screening;
import ee.tuleva.onboarding.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentPartyScreenerTest {

  private static final String REGISTRY_CODE = "14118923";

  @Mock SanctionAndPepScreener sanctionAndPepScreener;
  @Mock CompanySanctionCheck companySanctionCheck;
  @Mock UserRepository userRepository;

  @InjectMocks PaymentPartyScreener screener;

  @Test
  void screenPayer_screensAPayerWithARegistryCodeAsThatCompany() {
    given(companySanctionCheck.screeningOutcome(REGISTRY_CODE, "Some Company OÜ"))
        .willReturn(SANCTION_HIT);

    assertThat(screener.screenPayer(REGISTRY_CODE, "Some Company OÜ"))
        .isEqualTo(Screening.SANCTION_HIT);
    verifyNoInteractions(sanctionAndPepScreener);
  }

  @Test
  void screenPayer_screensAPayerWithoutACodeByName() {
    given(sanctionAndPepScreener.nameScreeningOutcome("Some Company OÜ")).willReturn(CLEAR);

    assertThat(screener.screenPayer(null, "Some Company OÜ")).isEqualTo(Screening.CLEAR);
    verifyNoInteractions(companySanctionCheck);
  }

  @Test
  void screenPayer_screensAPayerWhoseCodeIsNeitherAPersonalNorARegistryCodeByName() {
    given(sanctionAndPepScreener.nameScreeningOutcome("Some Company OÜ")).willReturn(CLEAR);

    assertThat(screener.screenPayer("not-a-code", "Some Company OÜ")).isEqualTo(Screening.CLEAR);
    verifyNoInteractions(companySanctionCheck);
  }
}
