package ee.tuleva.onboarding.kyb;

public record PersonalCode(String value) {

  @Override
  public String toString() {
    return value;
  }
}
