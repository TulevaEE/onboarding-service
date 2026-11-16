package ee.tuleva.onboarding.savings;

public enum SavingsFundOnboardingStatus {
  PENDING,
  ON_HOLD,
  REJECTED,
  COMPLETED;

  public SavingsFundOnboardingStatus shownToTheSaver() {
    return this == ON_HOLD ? PENDING : this;
  }
}
