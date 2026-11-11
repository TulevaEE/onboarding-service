package ee.tuleva.onboarding.rulebook;

public record Case<C, O>(String name, C context, O expected) {

  public Case {
    if (name.isBlank()) {
      throw new IllegalArgumentException("Case has no name: expected=" + expected);
    }
  }
}
