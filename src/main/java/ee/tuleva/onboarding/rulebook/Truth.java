package ee.tuleva.onboarding.rulebook;

public enum Truth {
  HOLDS("rakendub"),
  FAILS("ei rakendu");

  private final String ruleOutcome;

  Truth(final String ruleOutcome) {
    this.ruleOutcome = ruleOutcome;
  }

  public static Truth of(final boolean holds) {
    return holds ? HOLDS : FAILS;
  }

  Truth opposite() {
    return switch (this) {
      case HOLDS -> FAILS;
      case FAILS -> HOLDS;
    };
  }

  String ruleOutcome() {
    return ruleOutcome;
  }
}
