package ee.tuleva.onboarding.kyb;

public enum LegalForm {
  OÜ,
  AS,
  TÜ,
  UÜ,
  MTÜ,
  SA,
  FIE,
  TÜH,
  OTHER;

  public boolean isAccepted() {
    return this == OÜ;
  }

  public static LegalForm fromString(String value) {
    try {
      return valueOf(value);
    } catch (IllegalArgumentException e) {
      return OTHER;
    }
  }
}
