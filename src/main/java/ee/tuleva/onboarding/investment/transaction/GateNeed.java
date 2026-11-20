package ee.tuleva.onboarding.investment.transaction;

enum GateNeed {
  CLEARED_A_STOP,
  NOT_NEEDED;

  boolean neededByGate() {
    return this == CLEARED_A_STOP;
  }
}
