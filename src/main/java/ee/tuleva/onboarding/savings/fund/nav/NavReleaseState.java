package ee.tuleva.onboarding.savings.fund.nav;

public enum NavReleaseState {
  SENT,
  HELD,
  REVISION,
  NOT_FINAL;

  boolean isReleasable() {
    return this == HELD || this == REVISION;
  }
}
