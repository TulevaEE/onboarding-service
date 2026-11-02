package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.AmlCheckType.RISK_LEVEL;
import static ee.tuleva.onboarding.aml.AmlCheckType.RISK_LEVEL_OVERRIDE;
import static ee.tuleva.onboarding.aml.AmlCheckType.TKF_RISK_LEVEL;
import static ee.tuleva.onboarding.aml.AmlCheckType.TKF_RISK_LEVEL_OVERRIDE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.aml.RiskLevels;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class AmlCheckRiskLevelsTest {

  private static final String PERSONAL_CODE = "38888888888";
  private static final List<AmlCheckType> THIRD_PILLAR = List.of(RISK_LEVEL, RISK_LEVEL_OVERRIDE);
  private static final List<AmlCheckType> TKF = List.of(TKF_RISK_LEVEL, TKF_RISK_LEVEL_OVERRIDE);

  private final AmlCheckRepository amlCheckRepository = mock(AmlCheckRepository.class);
  private final RiskLevels amlCheckRiskLevels = new AmlCheckRiskLevels(amlCheckRepository);

  @Test
  void highRiskWhenTheLatestThirdPillarSnapshotIsHigh() {
    givenLatest(THIRD_PILLAR, checkWithLevel(RISK_LEVEL, 1));

    assertThat(amlCheckRiskLevels.isHighRisk(PERSONAL_CODE)).isTrue();
  }

  @Test
  void highRiskWhenTheLatestTkfOverrideLevelIsHighAsAString() {
    givenNone(THIRD_PILLAR);
    givenLatest(TKF, checkWithLevel(TKF_RISK_LEVEL_OVERRIDE, "1"));

    assertThat(amlCheckRiskLevels.isHighRisk(PERSONAL_CODE)).isTrue();
  }

  @Test
  void notHighRiskWhenTheLatestRowsAreNotHighOrMissing() {
    givenLatest(THIRD_PILLAR, checkWithLevel(RISK_LEVEL, 2));
    givenNone(TKF);

    assertThat(amlCheckRiskLevels.isHighRisk(PERSONAL_CODE)).isFalse();
  }

  @Test
  void highRiskWhenTheLevelIsMalformed() {
    givenLatest(THIRD_PILLAR, checkWithLevel(RISK_LEVEL, "not-a-number"));

    assertThat(amlCheckRiskLevels.isHighRisk(PERSONAL_CODE)).isTrue();
  }

  @Test
  void highRiskWhenAnAutomaticSnapshotHasNoLevel() {
    givenLatest(THIRD_PILLAR, checkWithLevel(RISK_LEVEL, null));

    assertThat(amlCheckRiskLevels.isHighRisk(PERSONAL_CODE)).isTrue();
  }

  @Test
  void notHighRiskWhenTheLatestRowsAreOverridesWithoutALevel() {
    givenLatest(THIRD_PILLAR, checkWithLevel(RISK_LEVEL_OVERRIDE, null));
    givenLatest(TKF, checkWithLevel(TKF_RISK_LEVEL_OVERRIDE, null));

    assertThat(amlCheckRiskLevels.isHighRisk(PERSONAL_CODE)).isFalse();
  }

  private void givenLatest(List<AmlCheckType> types, AmlCheck check) {
    given(
            amlCheckRepository.findFirstByPersonalCodeAndTypeInOrderByCreatedTimeDescIdDesc(
                PERSONAL_CODE, types))
        .willReturn(Optional.of(check));
  }

  private void givenNone(List<AmlCheckType> types) {
    given(
            amlCheckRepository.findFirstByPersonalCodeAndTypeInOrderByCreatedTimeDescIdDesc(
                PERSONAL_CODE, types))
        .willReturn(Optional.empty());
  }

  private static AmlCheck checkWithLevel(AmlCheckType type, @Nullable Object level) {
    Map<String, Object> metadata = new HashMap<>();
    if (level != null) {
      metadata.put("level", level);
    }
    return AmlCheck.builder()
        .personalCode(PERSONAL_CODE)
        .type(type)
        .success(false)
        .metadata(metadata)
        .build();
  }
}
