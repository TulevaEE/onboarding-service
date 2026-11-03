package ee.tuleva.onboarding.instrument;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

record BenchmarkProxyPeriods(Map<String, List<BenchmarkProxyPeriod>> byHolding) {

  static final BenchmarkProxyPeriods NONE = new BenchmarkProxyPeriods(Map.of());

  static BenchmarkProxyPeriods ofOrderedByValidUntil(List<BenchmarkProxyPeriod> periods) {
    return new BenchmarkProxyPeriods(
        Map.copyOf(
            periods.stream()
                .collect(groupingBy(BenchmarkProxyPeriod::holdingIsin, toUnmodifiableList()))));
  }

  Optional<String> proxyIsinOn(String holdingIsin, LocalDate date) {
    return byHolding.getOrDefault(holdingIsin, List.of()).stream()
        .filter(period -> period.covers(date))
        .map(BenchmarkProxyPeriod::proxyIsin)
        .findFirst();
  }
}
