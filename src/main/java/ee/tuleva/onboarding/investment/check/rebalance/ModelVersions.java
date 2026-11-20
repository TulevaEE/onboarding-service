package ee.tuleva.onboarding.investment.check.rebalance;

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
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.IntStream;

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
        .mapToObj(
            older ->
                ModelChange.between(
                    newestFirst.get(older - 1).getKey(),
                    newestFirst.get(older).getValue(),
                    newestFirst.get(older - 1).getValue()))
        .flatMap(Optional::stream)
        .toList();
  }

  Map<String, BigDecimal> weightsOn(LocalDate date) {
    return Optional.ofNullable(weightsByEffectiveDate.floorEntry(date))
        .map(Map.Entry::getValue)
        .orElseThrow(
            () -> new IllegalStateException("No model version on or before: date=" + date));
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
