package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.AmlCheckType.RISK_LEVEL;
import static ee.tuleva.onboarding.aml.AmlCheckType.RISK_LEVEL_OVERRIDE;
import static ee.tuleva.onboarding.aml.AmlCheckType.TKF_RISK_LEVEL;
import static ee.tuleva.onboarding.aml.AmlCheckType.TKF_RISK_LEVEL_OVERRIDE;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.aml.RiskLevels;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class AmlCheckRiskLevels implements RiskLevels {

  private static final int HIGH_RISK_LEVEL = 1;

  private final AmlCheckRepository amlCheckRepository;

  @Override
  public boolean isHighRisk(String personalCode) {
    return latestLevelIsHigh(personalCode, List.of(RISK_LEVEL, RISK_LEVEL_OVERRIDE))
        || latestLevelIsHigh(personalCode, List.of(TKF_RISK_LEVEL, TKF_RISK_LEVEL_OVERRIDE));
  }

  private boolean latestLevelIsHigh(String personalCode, List<AmlCheckType> types) {
    return amlCheckRepository
        .findFirstByPersonalCodeAndTypeInOrderByCreatedTimeDescIdDesc(personalCode, types)
        .map(this::isHighOrUnexpectedLevel)
        .orElse(false);
  }

  private boolean isHighOrUnexpectedLevel(AmlCheck check) {
    Object level = check.getMetadata().get("level");
    if (level == null) {
      return !isSpecialistConfirmationWithoutScoreChange(check);
    }
    Integer parsedLevel = parseLevel(level);
    if (parsedLevel == null) {
      log.error(
          "Malformed risk level metadata, treating as high risk: checkId={}, level={}",
          check.getId(),
          level);
      return true;
    }
    return parsedLevel == HIGH_RISK_LEVEL;
  }

  private boolean isSpecialistConfirmationWithoutScoreChange(AmlCheck checkWithoutLevel) {
    AmlCheckType type = checkWithoutLevel.getType();
    return type == RISK_LEVEL_OVERRIDE || type == TKF_RISK_LEVEL_OVERRIDE;
  }

  private @Nullable Integer parseLevel(Object level) {
    if (level instanceof Number number) {
      return number.intValue();
    }
    if (level instanceof String string) {
      try {
        return Integer.parseInt(string.trim());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }
}
