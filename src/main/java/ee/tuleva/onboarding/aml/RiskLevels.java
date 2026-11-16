package ee.tuleva.onboarding.aml;

public interface RiskLevels {

  PersonRiskLevel riskLevel(String personalCode) throws RiskLevelUnavailableException;

  boolean isHighRiskCompany(String registryCode) throws RiskLevelUnavailableException;
}
