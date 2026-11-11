package ee.tuleva.onboarding.rulebook;

import java.util.List;

public sealed interface Condition<C> permits Fact, Junction, Not {

  boolean test(C context);

  String text(Truth truth);

  List<Fact<C>> facts(C context);

  List<String> notes();

  default String textOn(final C context) {
    return text(Truth.of(test(context)));
  }

  default boolean joined() {
    return false;
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
