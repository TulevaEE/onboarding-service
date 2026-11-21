package ee.tuleva.onboarding.investment.check.rebalance;

import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

record ModelVersions(NavigableMap<LocalDate, Map<String, BigDecimal>> weightsByEffectiveDate) {

  static ModelVersions of(List<ModelPortfolioAllocation> history) {
    return new ModelVersions(
        history.stream()
            .collect(
                groupingBy(
                    ModelPortfolioAllocation::getEffectiveDate,
                    TreeMap::new,
                    collectingAndThen(toList(), ModelVersions::weightsByIsin))));
  }

  List<ModelChange> changesNewestFirst() {
    var newestFirst = List.copyOf(weightsByEffectiveDate.descendingMap().entrySet());
    return IntStream.range(1, newestFirst.size())
        .filter(
            older ->
                weightsDiffer(
                    newestFirst.get(older).getValue(), newestFirst.get(older - 1).getValue()))
        .mapToObj(older -> changeTakingEffectOn(newestFirst.get(older - 1)))
        .toList();
  }

  private ModelChange changeTakingEffectOn(Map.Entry<LocalDate, Map<String, BigDecimal>> version) {
    return new ModelChange(
        version.getKey(),
        version.getValue(),
        weightsByEffectiveDate.tailMap(version.getKey(), false));
  }

  private static boolean weightsDiffer(
      Map<String, BigDecimal> previousWeights, Map<String, BigDecimal> currentWeights) {
    return Stream.concat(previousWeights.keySet().stream(), currentWeights.keySet().stream())
        .anyMatch(
            isin -> weightOf(isin, previousWeights).compareTo(weightOf(isin, currentWeights)) != 0);
  }

  private static BigDecimal weightOf(String isin, Map<String, BigDecimal> weights) {
    return weights.getOrDefault(isin, ZERO);
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
}
