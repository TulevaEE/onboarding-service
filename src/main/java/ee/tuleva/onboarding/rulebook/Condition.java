package ee.tuleva.onboarding.rulebook;

import java.util.List;

public sealed interface Condition<C> permits Fact, Junction, Not {

  boolean test(C context);

  String text(Truth truth);

  List<String> notes();

  default List<Fact<C>> facts(final C context) {
    return switch (this) {
      case Fact<C> fact -> List.of(fact);
      case Not<C>(Condition<C> negated) -> negated.facts(context);
      case Junction<C> junction -> junction.decidingFacts(context);
    };
  }

  default boolean joined() {
    return this instanceof Junction<C>
        || (this instanceof Not<C>(Condition<C> negated) && negated.joined());
  }

  default String bracketed(final Truth truth) {
    return joined() ? "(" + text(truth) + ")" : text(truth);
  }

  @SafeVarargs
  static <C> Condition<C> and(final Condition<C>... conditions) {
    return new Junction<>(Junction.Kind.ALL, List.of(conditions));
  }

  @SafeVarargs
  static <C> Condition<C> or(final Condition<C>... conditions) {
    return new Junction<>(Junction.Kind.ANY, List.of(conditions));
  }

  static <C> Condition<C> not(final Condition<C> condition) {
    return condition instanceof Not<C>(Condition<C> negated) ? negated : new Not<>(condition);
  }
}
