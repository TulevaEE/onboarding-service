package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import java.util.Objects;
import java.util.stream.Stream;

final class InnermostCause {

  private InnermostCause() {}

  static String nameOf(Throwable failure) {
    return Stream.iterate(failure, Objects::nonNull, Throwable::getCause)
        .reduce((outer, inner) -> inner)
        .orElse(failure)
        .getClass()
        .getSimpleName();
  }
}
