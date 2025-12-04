package ee.tuleva.onboarding.kyc;

import java.util.Map;

public record KycCheck(RiskLevel riskLevel, Map<String, Object> metadata) {

  public enum RiskLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH
  }
}
