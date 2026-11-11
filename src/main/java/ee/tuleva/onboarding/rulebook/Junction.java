package ee.tuleva.onboarding.rulebook;

import static ee.tuleva.onboarding.rulebook.Truth.HOLDS;

import java.util.List;

record Junction<C>(Kind kind, List<Condition<C>> conditions) implements Condition<C> {

  enum Kind {
    ALL("ja", "või"),
    ANY("või", "ja");

    private final String whereItHolds;
    private final String whereItFails;

    Kind(final String whereItHolds, final String whereItFails) {
      this.whereItHolds = whereItHolds;
      this.whereItFails = whereItFails;
    }

    String conjunction(final Truth truth) {
      return truth == HOLDS ? whereItHolds : whereItFails;
    }
  }

  private static final int FEWEST_CONDITIONS = 2;

  Junction {
    if (conditions.size() < FEWEST_CONDITIONS) {
      throw new IllegalArgumentException(
          "Junction joins fewer than two conditions: conditions=" + conditions.size());
    }
    conditions = List.copyOf(conditions);
  }

  @Override
  public boolean test(final C context) {
    return switch (kind) {
      case ALL -> conditions.stream().allMatch(condition -> condition.test(context));
      case ANY -> conditions.stream().anyMatch(condition -> condition.test(context));
    };
  }

  @Override
  public String text(final Truth truth) {
    return Phrases.join(
        conditions.stream().map(condition -> condition.bracketed(truth)).toList(),
        kind.conjunction(truth));
  }

  List<Fact<C>> decidingFacts(final C context) {
    var holds = test(context);
    return conditions.stream()
        .filter(condition -> condition.test(context) == holds)
        .flatMap(condition -> condition.facts(context).stream())
        .distinct()
        .toList();
  }

  @Override
  public List<String> notes() {
    return conditions.stream().flatMap(condition -> condition.notes().stream()).distinct().toList();
  }
}
