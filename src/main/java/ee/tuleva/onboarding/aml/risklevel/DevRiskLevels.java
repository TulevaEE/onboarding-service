package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.PersonRiskLevel.Level.NOT_HIGH;

import ee.tuleva.onboarding.aml.PersonRiskLevel;
import ee.tuleva.onboarding.aml.RiskLevels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile({"dev", "staging"})
class DevRiskLevels implements RiskLevels {

  @Override
  public PersonRiskLevel riskLevel(String personalCode) {
    log.info("Person's risk level answered by the aml-service mock: highRisk=false");
    return new PersonRiskLevel(NOT_HIGH, null);
  }

  @Override
  public boolean isHighRiskCompany(String registryCode) {
    log.info(
        "Company's risk level answered by the aml-service mock: registryCode={}, highRisk=false",
        registryCode);
    return false;
  }
}
