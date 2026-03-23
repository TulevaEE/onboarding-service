package ee.tuleva.onboarding.kyb;

public enum CompanyStatus {
  R,
  L,
  N,
  K;

  public boolean isActive() {
    return this == R;
  }
}
