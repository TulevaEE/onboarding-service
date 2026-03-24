package ee.tuleva.onboarding.kyb;

public record RegistryCode(String value) {

  @Override
  public String toString() {
    return value;
  }
}
