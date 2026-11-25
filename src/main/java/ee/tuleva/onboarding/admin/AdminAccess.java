package ee.tuleva.onboarding.admin;

public enum AdminAccess {
  ADMIN("admin-token"),
  OPS("ops-token");

  private final String recordedAs;

  AdminAccess(String recordedAs) {
    this.recordedAs = recordedAs;
  }

  public String recordedAs() {
    return recordedAs;
  }
}
