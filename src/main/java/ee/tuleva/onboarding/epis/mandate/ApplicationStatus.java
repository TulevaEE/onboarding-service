package ee.tuleva.onboarding.epis.mandate;

public enum ApplicationStatus {
  COMPLETE,
  PENDING,
  FAILED;

  public boolean isPending() {
    return this == PENDING;
  }

  public boolean isComplete() {
    return this == COMPLETE;
  }
}
