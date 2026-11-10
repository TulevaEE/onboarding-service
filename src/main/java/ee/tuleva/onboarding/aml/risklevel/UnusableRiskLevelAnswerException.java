package ee.tuleva.onboarding.aml.risklevel;

class UnusableRiskLevelAnswerException extends RiskLevelsRefusedException {

  UnusableRiskLevelAnswerException(String reason) {
    super(reason);
  }
}
