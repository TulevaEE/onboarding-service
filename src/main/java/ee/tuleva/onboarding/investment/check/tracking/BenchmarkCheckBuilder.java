package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK_MODEL;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.instrument.BenchmarkProxy;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.SecurityData;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class BenchmarkCheckBuilder {

  private static final String MSCI_ACWI_KEY = "MSCI_ACWI";
  private static final int SCALE = 6;

  private static final Map<TulevaFund, BenchmarkConfig> BENCHMARK_CONFIGS =
      Map.of(
          TulevaFund.TUK75, new BenchmarkConfig(MSCI_ACWI_KEY),
          TulevaFund.TUK00,
              new BenchmarkConfig(
                  List.of(
                      new BenchmarkComponent("LU0826455353", new BigDecimal("0.50")),
                      new BenchmarkComponent("LU0839970364", new BigDecimal("0.50")))),
          TulevaFund.TUV100, new BenchmarkConfig(MSCI_ACWI_KEY),
          TulevaFund.TKF100, new BenchmarkConfig(MSCI_ACWI_KEY));

  private final TrackingDifferenceCalculator calculator;
  private final FundValueProvider fundValueProvider;
  private final PriorityPriceProvider priorityPriceProvider;
  private final BenchmarkLegResolver benchmarkLegResolver;
  private final ConsecutiveBreachTracker consecutiveBreachTracker;

  record BenchmarkConfig(@Nullable String singleKey, List<BenchmarkComponent> components) {
    BenchmarkConfig(String singleKey) {
      this(singleKey, List.of());
    }

    BenchmarkConfig(List<BenchmarkComponent> components) {
      this(null, components);
    }
  }

  record BenchmarkComponent(String key, BigDecimal weight) {}

  Optional<TrackingDifferenceResult> buildBenchmarkCheck(
      TulevaFund fund,
      LocalDate checkDate,
      LocalDate previousDate,
      FundValue todayNav,
      FundValue yesterdayNav) {

    var config = BENCHMARK_CONFIGS.get(fund);
    if (config == null) {
      return Optional.empty();
    }

    var benchmarkReturn = calculateBenchmarkReturn(config, checkDate, previousDate);
    if (benchmarkReturn.isEmpty()) {
      log.warn("Missing benchmark data: fund={}, checkDate={}", fund, checkDate);
      return Optional.empty();
    }

    var fundReturn =
        todayNav
            .value()
            .subtract(yesterdayNav.value())
            .divide(yesterdayNav.value(), 6, RoundingMode.HALF_UP);
    var td = fundReturn.subtract(benchmarkReturn.get());
    var breach = td.abs().compareTo(calculator.breachThreshold(checkDate)) >= 0;

    var priorBreaches =
        consecutiveBreachTracker.countConsecutiveBreaches(fund, BENCHMARK, checkDate);
    int days = breach ? priorBreaches.count() + 1 : 0;
    BigDecimal compFund = ZERO;
    BigDecimal compBenchmark = ZERO;
    BigDecimal compTd = ZERO;
    if (breach) {
      compFund =
          BigDecimal.ONE
              .add(priorBreaches.compoundedFundReturn())
              .multiply(BigDecimal.ONE.add(fundReturn))
              .subtract(BigDecimal.ONE);
      compBenchmark =
          BigDecimal.ONE
              .add(priorBreaches.compoundedBenchmarkReturn())
              .multiply(BigDecimal.ONE.add(benchmarkReturn.get()))
              .subtract(BigDecimal.ONE);
      compTd = compFund.subtract(compBenchmark);
    }

    var result =
        TrackingDifferenceResult.builder()
            .fund(fund)
            .checkDate(checkDate)
            .checkType(BENCHMARK)
            .trackingDifference(td)
            .fundReturn(fundReturn)
            .benchmarkReturn(benchmarkReturn.get())
            .breach(breach)
            .consecutiveBreachDays(days)
            .consecutiveNetTd(compTd)
            .compoundedFundReturn(compFund)
            .compoundedBenchmarkReturn(compBenchmark)
            .securityAttributions(List.of())
            .cashDrag(ZERO)
            .feeDrag(ZERO)
            .residual(ZERO)
            .build();

    return Optional.of(result);
  }

  private Optional<BigDecimal> calculateBenchmarkReturn(
      BenchmarkConfig config, LocalDate checkDate, LocalDate previousDate) {

    if (config.singleKey() != null) {
      var today = fundValueProvider.getLatestValue(config.singleKey(), checkDate);
      var yesterday = fundValueProvider.getLatestValue(config.singleKey(), previousDate);
      if (today.isEmpty() || yesterday.isEmpty() || yesterday.get().value().signum() == 0) {
        return Optional.empty();
      }
      return Optional.of(
          today
              .get()
              .value()
              .subtract(yesterday.get().value())
              .divide(yesterday.get().value(), 6, RoundingMode.HALF_UP));
    }

    var totalReturn = ZERO;
    for (var component : config.components()) {
      var today = priorityPriceProvider.resolve(component.key(), checkDate);
      var yesterday = priorityPriceProvider.resolve(component.key(), previousDate);
      if (today.isEmpty() || yesterday.isEmpty() || yesterday.get().value().signum() == 0) {
        return Optional.empty();
      }
      var componentReturn =
          today
              .get()
              .value()
              .subtract(yesterday.get().value())
              .divide(yesterday.get().value(), 6, RoundingMode.HALF_UP);
      totalReturn = totalReturn.add(component.weight().multiply(componentReturn));
    }
    return Optional.of(totalReturn.setScale(6, RoundingMode.HALF_UP));
  }

  Optional<TrackingDifferenceResult> buildBenchmarkModelCheck(
      TulevaFund fund, LocalDate checkDate, List<SecurityData> securities) {

    var validSecurities =
        securities.stream()
            .filter(
                s ->
                    s.today().price() != null
                        && s.previous().price() != null
                        && s.previous().price().signum() != 0
                        && s.today().date() != null
                        && s.previous().date() != null)
            .toList();

    if (validSecurities.isEmpty()) {
      return Optional.empty();
    }

    var maxDailyReturn = calculator.maxDailyReturn(checkDate);
    var totalWeightedReturn = ZERO;
    var totalWeightedBenchmarkReturn = ZERO;
    var totalWeight = ZERO;
    var attributions = new ArrayList<SecurityAttribution>();

    for (var s : validSecurities) {
      var benchmarkKey = resolveBenchmarkKey(s.isin());
      if (benchmarkKey == null) {
        continue;
      }
      var bmReturn =
          lookupReturn(
              benchmarkKey,
              s.today().requireDate(s.isin()),
              s.previous().requireDate(s.isin()),
              maxDailyReturn);
      if (bmReturn.isEmpty()) {
        log.warn(
            "Missing benchmark model data: fund={}, isin={}, benchmarkKey={}",
            fund,
            s.isin(),
            benchmarkKey);
        continue;
      }
      var secReturn =
          calculator.safeDailyReturn(
              s.today().requirePrice(s.isin()),
              s.previous().requirePrice(s.isin()),
              maxDailyReturn);
      var weight = s.actualWeight();
      var returnDiff = secReturn.subtract(bmReturn.get()).setScale(SCALE, RoundingMode.HALF_UP);
      var contribution = weight.multiply(returnDiff).setScale(SCALE, RoundingMode.HALF_UP);
      totalWeightedReturn = totalWeightedReturn.add(weight.multiply(secReturn));
      totalWeightedBenchmarkReturn =
          totalWeightedBenchmarkReturn.add(weight.multiply(bmReturn.get()));
      totalWeight = totalWeight.add(weight);
      attributions.add(
          new SecurityAttribution(
              s.isin(), null, weight, null, secReturn, bmReturn.get(), contribution));
    }

    if (totalWeight.signum() == 0) {
      return Optional.empty();
    }

    var benchmarkReturn =
        totalWeightedBenchmarkReturn.divide(totalWeight, SCALE, RoundingMode.HALF_UP);
    var instrumentReturn = totalWeightedReturn.divide(totalWeight, SCALE, RoundingMode.HALF_UP);
    var td = instrumentReturn.subtract(benchmarkReturn).setScale(SCALE, RoundingMode.HALF_UP);
    var breach = td.abs().compareTo(calculator.breachThreshold(checkDate)) >= 0;

    var priorBreaches =
        consecutiveBreachTracker.countConsecutiveBreaches(fund, BENCHMARK_MODEL, checkDate);
    int days = breach ? priorBreaches.count() + 1 : 0;
    BigDecimal compFund = ZERO;
    BigDecimal compBenchmark = ZERO;
    BigDecimal compTd = ZERO;
    Map<String, BigDecimal> escalationAttrs = null;
    if (breach) {
      compFund =
          BigDecimal.ONE
              .add(priorBreaches.compoundedFundReturn())
              .multiply(BigDecimal.ONE.add(instrumentReturn))
              .subtract(BigDecimal.ONE);
      compBenchmark =
          BigDecimal.ONE
              .add(priorBreaches.compoundedBenchmarkReturn())
              .multiply(BigDecimal.ONE.add(benchmarkReturn))
              .subtract(BigDecimal.ONE);
      compTd = compFund.subtract(compBenchmark);
      escalationAttrs =
          TrackingDifferenceEventMapper.mergeAttributions(
              priorBreaches.contributionByIsin(), attributions);
    }

    return Optional.of(
        TrackingDifferenceResult.builder()
            .fund(fund)
            .checkDate(checkDate)
            .checkType(BENCHMARK_MODEL)
            .trackingDifference(td)
            .fundReturn(instrumentReturn)
            .benchmarkReturn(benchmarkReturn)
            .breach(breach)
            .consecutiveBreachDays(days)
            .consecutiveNetTd(compTd)
            .compoundedFundReturn(compFund)
            .compoundedBenchmarkReturn(compBenchmark)
            .escalationAttributions(escalationAttrs)
            .securityAttributions(List.copyOf(attributions))
            .cashDrag(ZERO)
            .feeDrag(ZERO)
            .residual(ZERO)
            .build());
  }

  private @Nullable String resolveBenchmarkKey(String isin) {
    return benchmarkLegResolver.resolve(isin).map(BenchmarkProxy::storageKey).orElse(null);
  }

  private Optional<BigDecimal> lookupReturn(
      String key, LocalDate todayDate, LocalDate previousDate, BigDecimal maxDailyReturn) {
    var today = fundValueProvider.getLatestValue(key, todayDate);
    var yesterday = fundValueProvider.getLatestValue(key, previousDate);
    if (today.isEmpty() || yesterday.isEmpty() || yesterday.get().value().signum() == 0) {
      return Optional.empty();
    }
    return Optional.of(
        calculator.safeDailyReturn(today.get().value(), yesterday.get().value(), maxDailyReturn));
  }
}
