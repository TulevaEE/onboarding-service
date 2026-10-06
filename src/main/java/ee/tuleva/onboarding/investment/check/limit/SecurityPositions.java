package ee.tuleva.onboarding.investment.check.limit;

import static java.util.Comparator.comparing;
import static java.util.function.BinaryOperator.maxBy;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.position.FundPosition;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

final class SecurityPositions {

  private static final Comparator<FundPosition> WRITTEN_LAST_THEN_INSERTED_LAST =
      comparing(SecurityPositions::lastWrittenAt).thenComparing(FundPosition::getId);

  private SecurityPositions() {}

  static List<FundPosition> mostRecentlyWrittenRowPerIsin(List<FundPosition> positions) {
    return List.copyOf(
        positions.stream()
            .filter(position -> position.getAccountId() != null)
            .collect(
                toMap(
                    position -> Objects.requireNonNull(position.getAccountId()),
                    identity(),
                    maxBy(WRITTEN_LAST_THEN_INSERTED_LAST),
                    LinkedHashMap::new))
            .values());
  }

  private static Instant lastWrittenAt(FundPosition position) {
    return Objects.requireNonNullElse(position.getUpdatedAt(), position.getCreatedAt());
  }
}
