package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.safeDailyReturn;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.instrument.BenchmarkProxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BenchmarkPrices {

  private final FundValueProvider fundValueProvider;
  private final PriorityPriceProvider priorityPriceProvider;

  record LegReturn(BigDecimal value, List<StaleBenchmarkPrice> stalePrices) {}

  Optional<FundValue> indexValue(String key, LocalDate date) {
    return fundValueProvider.getLatestValue(key, date);
  }

  Optional<FundValue> instrumentPrice(String isin, LocalDate date) {
    return priorityPriceProvider.resolve(isin, date);
  }

  Optional<LegReturn> legReturn(
      BenchmarkProxy benchmark,
      LocalDate previousDate,
      LocalDate todayDate,
      BigDecimal maxDailyReturn) {
    var today = price(benchmark, todayDate);
    var previous = price(benchmark, previousDate);
    if (today.isEmpty() || previous.isEmpty() || previous.get().value().signum() == 0) {
      return Optional.empty();
    }
    var stalePrices =
        Stream.of(
                staleness(benchmark, todayDate, today.get()),
                staleness(benchmark, previousDate, previous.get()))
            .flatMap(Optional::stream)
            .distinct()
            .toList();
    return Optional.of(
        new LegReturn(
            safeDailyReturn(today.get().value(), previous.get().value(), maxDailyReturn),
            stalePrices));
  }

  private Optional<FundValue> price(BenchmarkProxy benchmark, LocalDate date) {
    var proxyInstrument = benchmark.proxyInstrument();
    if (proxyInstrument == null) {
      return indexValue(benchmark.storageKey(), date);
    }
    return instrumentPrice(proxyInstrument.getIsin(), date);
  }

  private static Optional<StaleBenchmarkPrice> staleness(
      BenchmarkProxy benchmark, LocalDate wanted, FundValue found) {
    if (!found.date().isBefore(wanted)) {
      return Optional.empty();
    }
    return Optional.of(new StaleBenchmarkPrice(name(benchmark), wanted, found.date()));
  }

  private static String name(BenchmarkProxy benchmark) {
    var proxyInstrument = benchmark.proxyInstrument();
    return proxyInstrument == null ? benchmark.storageKey() : proxyInstrument.getIsin();
  }
}
