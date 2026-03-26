package ee.tuleva.onboarding.kyb;

public enum LegalForm {
  OÜ,
  AS,
  TÜ,
  UÜ,
  MTÜ,
  SA,
  FIE;

  public boolean isAccepted() {
    return this == OÜ;
  }
}
