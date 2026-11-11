package ee.tuleva.onboarding.rulebook;

public record Branch<C, O>(O outcome, String label, Condition<C> when) {

  public Branch {
    if (label.isBlank()) {
      throw new IllegalArgumentException("Branch has no label: outcome=" + outcome);
    }
  }
}
