package ee.tuleva.onboarding.rulebook;

import java.util.List;
import java.util.function.Predicate;

public record Fact<C>(Phrase phrase, Predicate<C> predicate) implements Condition<C> {

  @Override
  public boolean test(final C context) {
    return predicate.test(context);
  }

  @Override
  public String text(final Truth truth) {
    return phrase.text(truth);
  }

  @Override
  public List<Fact<C>> facts(final C context) {
    return List.of(this);
  }

  @Override
  public List<String> notes() {
    return phrase.notes();
  }
}
