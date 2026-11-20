package ee.tuleva.onboarding.investment.check.rebalance;

import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

record ModelChange(LocalDate effectiveDate, Set<String> changedIsins) {

  static Optional<ModelChange> between(
      LocalDate effectiveDate,
      Map<String, BigDecimal> previousWeights,
      Map<String, BigDecimal> currentWeights) {
    var changedIsins =
        Stream.concat(previousWeights.keySet().stream(), currentWeights.keySet().stream())
            .filter(
                isin ->
                    weightOf(isin, previousWeights).compareTo(weightOf(isin, currentWeights)) != 0)
            .collect(toUnmodifiableSet());
    return changedIsins.isEmpty()
        ? Optional.empty()
        : Optional.of(new ModelChange(effectiveDate, changedIsins));
  }

  private static BigDecimal weightOf(String isin, Map<String, BigDecimal> weights) {
    return weights.getOrDefault(isin, ZERO);
  }
}
