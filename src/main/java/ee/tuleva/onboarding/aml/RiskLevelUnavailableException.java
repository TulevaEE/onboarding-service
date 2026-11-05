package ee.tuleva.onboarding.aml;

public class RiskLevelUnavailableException extends RuntimeException {

  public RiskLevelUnavailableException(String reason) {
    super(reason);
  }
}
