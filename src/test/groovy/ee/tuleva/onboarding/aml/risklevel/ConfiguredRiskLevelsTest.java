package ee.tuleva.onboarding.aml.risklevel;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.aml.RiskLevelUnavailableException;
import ee.tuleva.onboarding.aml.RiskLevels;
import org.junit.jupiter.api.Test;

class ConfiguredRiskLevelsTest {

  private static final String PERSONAL_CODE = "38888888888";

  private final AmlCheckRiskLevels amlCheckRiskLevels = mock(AmlCheckRiskLevels.class);
  private final EffectiveRiskLevels effectiveRiskLevels = mock(EffectiveRiskLevels.class);

  @Test
  void answersFromTheLatestAmlChecksWhileHighRiskFromAmlServiceIsDisabled() {
    given(amlCheckRiskLevels.isHighRisk(PERSONAL_CODE)).willReturn(true);

    assertThat(withHighRiskFromAmlChecks().isHighRisk(PERSONAL_CODE)).isTrue();
    verifyNoInteractions(effectiveRiskLevels);
  }

  @Test
  void asksAmlServiceForTheEffectiveLevelsOnceHighRiskFromAmlServiceIsEnabled() {
    given(effectiveRiskLevels.isHighRisk(PERSONAL_CODE)).willReturn(true);

    assertThat(withHighRiskFromAmlService().isHighRisk(PERSONAL_CODE)).isTrue();
    verifyNoInteractions(amlCheckRiskLevels);
  }

  @Test
  void notHighRiskWhenAmlServiceSaysSoEvenIfTheLatestAmlCheckIsHigh() {
    given(amlCheckRiskLevels.isHighRisk(PERSONAL_CODE)).willReturn(true);
    given(effectiveRiskLevels.isHighRisk(PERSONAL_CODE)).willReturn(false);

    assertThat(withHighRiskFromAmlService().isHighRisk(PERSONAL_CODE)).isFalse();
  }

  @Test
  void passesOnThatAmlServiceCouldNotAnswerOnceHighRiskFromAmlServiceIsEnabled() {
    willThrow(new RiskLevelUnavailableException("aml-service could not answer"))
        .given(effectiveRiskLevels)
        .isHighRisk(PERSONAL_CODE);

    assertThatThrownBy(() -> withHighRiskFromAmlService().isHighRisk(PERSONAL_CODE))
        .isInstanceOf(RiskLevelUnavailableException.class);
    verifyNoInteractions(amlCheckRiskLevels);
  }

  private RiskLevels withHighRiskFromAmlChecks() {
    return riskLevelsWith(
        new AmlServiceProperties(
            false,
            "https://aml-service.test",
            "test-api-token-of-at-least-thirty-two-characters",
            ofSeconds(5),
            ofSeconds(60),
            false,
            ofSeconds(1),
            ofSeconds(2),
            ofSeconds(1),
            ofSeconds(5)));
  }

  private RiskLevels withHighRiskFromAmlService() {
    return riskLevelsWith(
        new AmlServiceProperties(
            false,
            "https://aml-service.test",
            "test-api-token-of-at-least-thirty-two-characters",
            ofSeconds(5),
            ofSeconds(60),
            true,
            ofSeconds(1),
            ofSeconds(2),
            ofSeconds(1),
            ofSeconds(5)));
  }

  private RiskLevels riskLevelsWith(AmlServiceProperties properties) {
    return new ConfiguredRiskLevels(amlCheckRiskLevels, effectiveRiskLevels, properties);
  }
}
