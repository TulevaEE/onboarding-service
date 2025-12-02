package ee.tuleva.onboarding.kyc;

public record KycCheck(int score, RiskLevel riskLevel) {

  public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH
  }
}
