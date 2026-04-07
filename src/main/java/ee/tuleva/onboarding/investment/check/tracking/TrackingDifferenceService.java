package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.SecurityData;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.TrackingInput;
import ee.tuleva.onboarding.investment.fees.FeeRate;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class TrackingDifferenceService {

  private static final String MSCI_ACWI_KEY = "MSCI_ACWI";
  private static final int ESCALATION_LOOKBACK = 2;

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

  private final Clock clock;
  private final FundPositionRepository fundPositionRepository;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final FundValueProvider fundValueProvider;
  private final PriorityPriceProvider priorityPriceProvider;
  private final FeeRateRepository feeRateRepository;
  private final TrackingDifferenceEventRepository eventRepository;
  private final TrackingDifferenceCalculator calculator;
  private final PublicHolidays publicHolidays;

  List<TrackingDifferenceResult> runChecks() {
    return runChecksAsOf(LocalDate.now(clock));
  }

  List<TrackingDifferenceResult> runChecksAsOf(LocalDate asOfDate) {
    var results = new ArrayList<TrackingDifferenceResult>();

    for (var fund : TulevaFund.values()) {
      var latestDate = fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, asOfDate);
      if (latestDate.isEmpty()) {
        log.warn("No position data for fund: fund={}, asOfDate={}", fund, asOfDate);
        continue;
      }

      var checkDate = latestDate.get();
      checkFund(fund, checkDate).forEach(results::add);
    }

    return results;
  }

  List<TrackingDifferenceResult> backfillChecks(int daysBack) {
    var today = LocalDate.now(clock);
    var allResults = new ArrayList<TrackingDifferenceResult>();

    for (int i = daysBack; i >= 0; i--) {
      var asOfDate = today.minusDays(i);
      allResults.addAll(runChecksAsOf(asOfDate));
    }

    return allResults;
  }

  private List<TrackingDifferenceResult> checkFund(TulevaFund fund, LocalDate checkDate) {
    var results = new ArrayList<TrackingDifferenceResult>();

    var previousDate = findPreviousNavDate(fund, checkDate);
    if (previousDate.isEmpty()) {
      log.warn("No previous NAV date: fund={}, checkDate={}", fund, checkDate);
      return results;
    }

    var todayNav = fundValueProvider.getLatestValue(fund.getIsin(), checkDate);
    var yesterdayNav = fundValueProvider.getLatestValue(fund.getIsin(), previousDate.get());

    if (todayNav.isEmpty() || yesterdayNav.isEmpty()) {
      log.warn(
          "Missing NAV data: fund={}, checkDate={}, todayNav={}, yesterdayNav={}",
          fund,
          checkDate,
          todayNav.isPresent(),
          yesterdayNav.isPresent());
      return results;
    }

    var allocations = modelPortfolioAllocationRepository.findLatestByFund(fund);
    if (allocations.isEmpty()) {
      log.warn("No model portfolio for fund: fund={}", fund);
      return results;
    }

    var positions =
        fundPositionRepository.findByNavDateAndFundAndAccountType(checkDate, fund, SECURITY);
    var totalNav =
        fundPositionRepository.sumMarketValueByFundAndAccountTypes(
            fund, checkDate, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY));
    var cashTotal =
        fundPositionRepository.sumMarketValueByFundAndAccountTypes(fund, checkDate, List.of(CASH));

    var cashWeight =
        totalNav.signum() != 0 ? cashTotal.divide(totalNav, 6, RoundingMode.HALF_UP) : ZERO;

    var annualFeeRate =
        feeRateRepository
            .findValidRate(fund, FeeType.MANAGEMENT, checkDate)
            .map(FeeRate::annualRate)
            .orElse(ZERO);

    var securities =
        buildSecurityData(allocations, positions, totalNav, checkDate, previousDate.get());

    var priorBreaches = countConsecutiveBreaches(fund, MODEL_PORTFOLIO, checkDate);

    var modelInput =
        TrackingInput.builder()
            .fund(fund)
            .checkDate(checkDate)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(todayNav.get().value())
            .yesterdayNav(yesterdayNav.get().value())
            .securities(securities)
            .cashWeight(cashWeight)
            .annualFeeRate(annualFeeRate)
            .consecutiveBreachDays(priorBreaches.count())
            .build();

    calculator
        .calculate(modelInput)
        .ifPresent(
            result -> {
              var withConsecutive = updateConsecutiveCount(result, priorBreaches);
              saveEvent(withConsecutive);
              results.add(withConsecutive);
            });

    buildBenchmarkCheck(fund, checkDate, previousDate.get(), todayNav.get(), yesterdayNav.get())
        .ifPresent(
            result -> {
              saveEvent(result);
              results.add(result);
            });

    return results;
  }

  private Optional<TrackingDifferenceResult> buildBenchmarkCheck(
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
    var breach = td.abs().compareTo(TrackingDifferenceCalculator.BREACH_THRESHOLD) >= 0;

    var priorBreaches = countConsecutiveBreaches(fund, BENCHMARK, checkDate);
    int days = breach ? priorBreaches.count() + 1 : 0;
    var netTd = breach ? priorBreaches.netTd().add(td) : ZERO;

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
            .consecutiveNetTd(netTd)
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

  private List<SecurityData> buildSecurityData(
      List<ModelPortfolioAllocation> allocations,
      List<FundPosition> positions,
      BigDecimal totalNav,
      LocalDate checkDate,
      LocalDate previousDate) {

    Map<String, BigDecimal> positionsByIsin =
        positions.stream()
            .filter(p -> p.getAccountId() != null)
            .collect(
                Collectors.toMap(
                    FundPosition::getAccountId, FundPosition::getMarketValue, BigDecimal::add));

    return allocations.stream()
        .filter(a -> a.getIsin() != null)
        .map(
            a -> {
              var todayPrice = priorityPriceProvider.resolve(a.getIsin(), checkDate);
              var yesterdayPrice = priorityPriceProvider.resolve(a.getIsin(), previousDate);

              var actualMarketValue = positionsByIsin.getOrDefault(a.getIsin(), ZERO);
              var actualWeight =
                  totalNav.signum() != 0
                      ? actualMarketValue.divide(totalNav, 6, RoundingMode.HALF_UP)
                      : ZERO;

              return new SecurityData(
                  a.getIsin(),
                  a.getWeight(),
                  actualWeight,
                  todayPrice.map(FundValue::value).orElse(null),
                  yesterdayPrice.map(FundValue::value).orElse(null));
            })
        .toList();
  }

  private Optional<LocalDate> findPreviousNavDate(TulevaFund fund, LocalDate checkDate) {
    var previousWorkingDay = publicHolidays.previousWorkingDay(checkDate);
    return fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, previousWorkingDay);
  }

  private ConsecutiveBreachInfo countConsecutiveBreaches(
      TulevaFund fund, TrackingCheckType checkType, LocalDate checkDate) {
    var recent =
        eventRepository.findMostRecentEvents(fund, checkType, checkDate, ESCALATION_LOOKBACK);
    int count = 0;
    var netTd = ZERO;
    for (var event : recent) {
      if (!event.isBreach()) {
        break;
      }
      count++;
      netTd = netTd.add(event.getTrackingDifference());
    }
    return new ConsecutiveBreachInfo(count, netTd);
  }

  private TrackingDifferenceResult updateConsecutiveCount(
      TrackingDifferenceResult result, ConsecutiveBreachInfo priorBreaches) {
    if (!result.breach()) {
      return result.toBuilder().consecutiveBreachDays(0).consecutiveNetTd(ZERO).build();
    }
    int days = priorBreaches.count() + 1;
    var netTd = priorBreaches.netTd().add(result.trackingDifference());
    return result.toBuilder().consecutiveBreachDays(days).consecutiveNetTd(netTd).build();
  }

  record ConsecutiveBreachInfo(int count, BigDecimal netTd) {}

  private void saveEvent(TrackingDifferenceResult result) {
    eventRepository.deleteByFundAndCheckDateAndCheckType(
        result.fund(), result.checkDate(), result.checkType());

    var event =
        TrackingDifferenceEvent.builder()
            .fund(result.fund())
            .checkDate(result.checkDate())
            .checkType(result.checkType())
            .trackingDifference(result.trackingDifference())
            .fundReturn(result.fundReturn())
            .benchmarkReturn(result.benchmarkReturn())
            .breach(result.breach())
            .consecutiveBreachDays(result.consecutiveBreachDays())
            .result(buildResultMap(result))
            .build();

    eventRepository.save(event);
  }

  private Map<String, Object> buildResultMap(TrackingDifferenceResult result) {
    var attributions =
        result.securityAttributions().stream()
            .map(
                a ->
                    Map.<String, Object>of(
                        "isin", a.isin(),
                        "modelWeight", a.modelWeight(),
                        "actualWeight", a.actualWeight(),
                        "weightDifference", a.weightDifference(),
                        "securityReturn", a.securityReturn(),
                        "contribution", a.contribution()))
            .toList();

    return Map.of(
        "securityAttributions",
        attributions,
        "cashDrag",
        Objects.requireNonNullElse(result.cashDrag(), ZERO),
        "feeDrag",
        Objects.requireNonNullElse(result.feeDrag(), ZERO),
        "residual",
        Objects.requireNonNullElse(result.residual(), ZERO));
  }

  record BenchmarkConfig(String singleKey, List<BenchmarkComponent> components) {
    BenchmarkConfig(String singleKey) {
      this(singleKey, List.of());
    }

    BenchmarkConfig(List<BenchmarkComponent> components) {
      this(null, components);
    }
  }

  record BenchmarkComponent(String key, BigDecimal weight) {}
}
