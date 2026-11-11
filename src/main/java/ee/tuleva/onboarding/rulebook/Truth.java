package ee.tuleva.onboarding.rulebook;

public enum Truth {
  HOLDS,
  FAILS;

  public static Truth of(final boolean holds) {
    return holds ? HOLDS : FAILS;
  }

  Truth opposite() {
    return switch (this) {
      case HOLDS -> FAILS;
      case FAILS -> HOLDS;
    };
  }
}
