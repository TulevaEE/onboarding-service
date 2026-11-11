package ee.tuleva.onboarding.rulebook;

import java.util.List;

record Not<C>(Condition<C> negated) implements Condition<C> {

  @Override
  public boolean test(final C context) {
    return !negated.test(context);
  }

  @Override
  public String text(final Truth truth) {
    return negated.text(truth.opposite());
  }

  @Override
  public List<String> notes() {
    return negated.notes();
  }
}
