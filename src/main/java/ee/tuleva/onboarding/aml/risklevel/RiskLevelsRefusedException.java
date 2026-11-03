package ee.tuleva.onboarding.aml.risklevel;

class RiskLevelsRefusedException extends IllegalStateException {

  private final String reason;

  RiskLevelsRefusedException(String reason) {
    super(reason);
    this.reason = reason;
  }

  String reason() {
    return reason;
  }
}
