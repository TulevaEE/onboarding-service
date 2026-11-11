package ee.tuleva.onboarding.rulebook;

import static ee.tuleva.onboarding.rulebook.Truth.FAILS;
import static ee.tuleva.onboarding.rulebook.Truth.HOLDS;

public record Example<C>(String name, C context, Truth labelled) {

  public Example {
    if (name.isBlank()) {
      throw new IllegalArgumentException("Example has no name: labelled=" + labelled);
    }
  }

  public static <C> Example<C> applies(final String name, final C context) {
    return new Example<>(name, context, HOLDS);
  }

  public static <C> Example<C> doesNotApply(final String name, final C context) {
    return new Example<>(name, context, FAILS);
  }
}
