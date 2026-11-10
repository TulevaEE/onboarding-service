package ee.tuleva.onboarding.aml;

public interface RiskLevels {

  boolean isHighRisk(String personalCode) throws RiskLevelUnavailableException;

  boolean isHighRiskCompany(String registryCode) throws RiskLevelUnavailableException;
}
