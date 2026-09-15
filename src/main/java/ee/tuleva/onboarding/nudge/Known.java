package ee.tuleva.onboarding.nudge;

enum Known {
  YES(true, false),
  NO(false, true),
  UNKNOWN(false, false);

  private final boolean yes;
  private final boolean no;

  Known(boolean yes, boolean no) {
    this.yes = yes;
    this.no = no;
  }

  static Known of(boolean value) {
    return value ? YES : NO;
  }

  boolean isYes() {
    return yes;
  }

  boolean isNo() {
    return no;
  }

  boolean isKnown() {
    return yes || no;
  }
}
