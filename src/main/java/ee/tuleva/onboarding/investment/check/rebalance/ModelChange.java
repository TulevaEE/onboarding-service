package ee.tuleva.onboarding.investment.check.rebalance;

import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableSet;

import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

record ModelChange(
    LocalDate effectiveDate, Map<String, BigDecimal> modelWeights, Set<String> changedIsins) {

  static Optional<ModelChange> between(
      List<ModelPortfolioAllocation> previous, List<ModelPortfolioAllocation> current) {
    if (previous.isEmpty() || current.isEmpty()) {
      return Optional.empty();
    }
    var previousWeights = weightsByIsin(previous);
    var currentWeights = weightsByIsin(current);
    var changedIsins =
        Stream.concat(previousWeights.keySet().stream(), currentWeights.keySet().stream())
            .filter(
                isin ->
                    weightOf(isin, previousWeights).compareTo(weightOf(isin, currentWeights)) != 0)
            .collect(toUnmodifiableSet());
    if (changedIsins.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new ModelChange(current.getFirst().getEffectiveDate(), currentWeights, changedIsins));
  }

  private static Map<String, BigDecimal> weightsByIsin(List<ModelPortfolioAllocation> version) {
    return version.stream()
        .filter(allocation -> allocation.getIsin() != null)
        .collect(
            toMap(
                allocation -> Objects.requireNonNull(allocation.getIsin()),
                ModelPortfolioAllocation::getWeight,
                BigDecimal::add));
  }

  private static BigDecimal weightOf(String isin, Map<String, BigDecimal> weights) {
    return weights.getOrDefault(isin, ZERO);
  }
}
