package ee.tuleva.onboarding.analytics;

public record SaverId(Type type, String code) {

  public enum Type {
    PERSON,
    LEGAL_ENTITY
  }

  public static SaverId person(String personalCode) {
    return new SaverId(Type.PERSON, personalCode);
  }
}
