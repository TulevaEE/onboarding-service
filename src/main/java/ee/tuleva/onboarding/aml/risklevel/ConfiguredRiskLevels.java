package ee.tuleva.onboarding.aml.risklevel;

import ee.tuleva.onboarding.aml.RiskLevels;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class ConfiguredRiskLevels implements RiskLevels {

  private final AmlCheckRiskLevels amlCheckRiskLevels;
  private final EffectiveRiskLevels effectiveRiskLevels;
  private final AmlServiceProperties amlServiceProperties;

  @Override
  public boolean isHighRisk(String personalCode) {
    return amlServiceProperties.highRiskEnabled()
        ? effectiveRiskLevels.isHighRisk(personalCode)
        : amlCheckRiskLevels.isHighRisk(personalCode);
  }
}
