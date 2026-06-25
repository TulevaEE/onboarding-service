package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.*;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.PriorityPriceProvider;
import ee.tuleva.onboarding.comparisons.fundvalue.ValidationStatus;
import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.PriceSnapshot;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.SecurityData;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.TrackingInput;
import ee.tuleva.onboarding.investment.fees.FeeRate;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class TrackingDifferenceService {

  private static final String MSCI_ACWI_KEY = "MSCI_ACWI";
  private static final int ESCALATION_LOOKBACK_FALLBACK = 10;
  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");
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

  private final Clock clock;
  private final FundPositionRepository fundPositionRepository;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  // External feeds (benchmarks, ETF prices). Fund's own NAV no longer comes from here.
  private final FundValueProvider fundValueProvider;
  private final PriorityPriceProvider priorityPriceProvider;
  private final PositionPriceResolver positionPriceResolver;
  private final PublicHolidays publicHolidays;
  private final FeeRateRepository feeRateRepository;
  private final TrackingDifferenceEventRepository eventRepository;
  private final TrackingDifferenceCalculator calculator;
  // Tuleva-internal source of truth for fund's own NAV per unit, populated by every NAV
  // calculation run (regardless of whether publishing succeeds).
  private final FundNavQueryService fundNavQueryService;

  List<TrackingDifferenceResult> runChecks() {
    return runChecksAsOf(LocalDate.now(clock), List.of(TulevaFund.values()));
  }

  List<TrackingDifferenceResult> runChecksForFunds(List<TulevaFund> funds) {
    return runChecksAsOf(LocalDate.now(clock), funds);
  }

  List<TrackingDifferenceResult> runChecksAsOf(LocalDate asOfDate) {
    return runChecksAsOf(asOfDate, List.of(TulevaFund.values()));
  }

  private List<TrackingDifferenceResult> runChecksAsOf(LocalDate asOfDate, List<TulevaFund> funds) {
    var results = new ArrayList<TrackingDifferenceResult>();
    var incompleteChecks = new ArrayList<String>();

    for (var fund : funds) {
      var checkDate =
          fundNavQueryService.findLatestNavDateOnOrBefore(fund.getCode(), asOfDate).orElse(null);
      if (checkDate == null) {
        log.warn("No NAV data for fund: fund={}, asOfDate={}", fund, asOfDate);
        continue;
      }

      try {
        checkFund(fund, checkDate).forEach(results::add);
      } catch (IncompletePriceDataException e) {
        log.warn("Skipping fund due to incomplete price data: {}", e.getMessage());
        incompleteChecks.add(e.getMessage());
      }
    }

    if (!incompleteChecks.isEmpty()) {
      throw new IncompletePriceDataException(
          "Incomplete security price data:\n" + String.join("\n", incompleteChecks), results);
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

  List<TrackingDifferenceResult> checkFund(TulevaFund fund, LocalDate checkDate) {
    var results = new ArrayList<TrackingDifferenceResult>();

    // Fund's own NAV comes from nav_report (Tuleva-internal). Today's row is persisted by
    // NavPublisher BEFORE the gate runs, so it is always present when called from the gate.
    // For yesterday we walk back to the previous working day rather than relying on the
    // fundValueProvider's at-or-before fallback.
    var previousDate = publicHolidays.previousWorkingDay(checkDate);
    var todayValue = fundNavQueryService.findNavPerUnit(fund.getCode(), checkDate);
    var yesterdayValue = fundNavQueryService.findNavPerUnit(fund.getCode(), previousDate);

    if (todayValue.isEmpty() || yesterdayValue.isEmpty()) {
      log.warn(
          "Missing NAV data: fund={}, checkDate={}, previousDate={}, todayNav={}, yesterdayNav={}",
          fund,
          checkDate,
          previousDate,
          todayValue.isPresent(),
          yesterdayValue.isPresent());
      return results;
    }

    var todayNav =
        new FundValue(fund.getIsin(), checkDate, todayValue.get(), "TULEVA", clock.instant());
    var yesterdayNav =
        new FundValue(
            fund.getIsin(), previousDate, yesterdayValue.get(), "TULEVA", clock.instant());

    var allocations = modelPortfolioAllocationRepository.findLatestByFundAsOf(fund, checkDate);
    if (allocations.isEmpty()) {
      log.warn("No model portfolio for fund: fund={}", fund);
      return results;
    }
    var previousAllocations =
        modelPortfolioAllocationRepository.findPreviousByFundAsOf(fund, checkDate);

    var positions =
        fundPositionRepository.findByNavDateAndFundAndAccountType(checkDate, fund, SECURITY);
    var totalNav =
        fundPositionRepository.sumMarketValueByFundAndAccountTypes(
            fund, checkDate, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY));
    var cashTotal =
        fundPositionRepository.sumMarketValueByFundAndAccountTypes(fund, checkDate, List.of(CASH));

    var cashWeight =
        totalNav.signum() != 0 ? cashTotal.divide(totalNav, 6, RoundingMode.HALF_UP) : ZERO;

    // Security weights are normalized to the securities sleeve (not total NAV) so that the
    // weight-deviation contributions sum to ~zero and the cash dilution is attributed once,
    // via cashDrag — not double-counted into per-instrument deviations. Matches the periodic
    // PeriodicTdAttributionService basis.
    var totalSecurities =
        positions.stream()
            .map(FundPosition::getMarketValue)
            .filter(Objects::nonNull)
            .reduce(ZERO, BigDecimal::add);

    var annualFeeRate =
        feeRateRepository
            .findValidRate(fund, FeeType.MANAGEMENT, checkDate)
            .map(FeeRate::annualRate)
            .orElse(ZERO);

    var securities =
        buildSecurityData(
            fund,
            allocations,
            previousAllocations,
            positions,
            totalSecurities,
            checkDate,
            previousDate);

    var missingPrices =
        securities.stream()
            .filter(s -> s.modelWeight().signum() > 0)
            .filter(s -> s.today().price() == null || s.previous().price() == null)
            .map(SecurityData::isin)
            .toList();
    if (!missingPrices.isEmpty()) {
      throw new IncompletePriceDataException(
          "fund=%s, checkDate=%s, missingIsins=%s".formatted(fund, checkDate, missingPrices),
          List.of());
    }

    var blendedSecurities =
        blendTransitionWeights(securities, allocations, previousAllocations, positions, fund);

    // NAV-correctness basis: holdings the fund actually held entering checkDate (the previous fund
    // NAV date's EOD snapshot), used to neutralise MOC trade-day timing in navResidual. Fails soft:
    // when the begin-of-day snapshot or any of its prices are unavailable we skip the navResidual
    // gate (warn, leave it null) rather than block the NAV report or manufacture a residual from
    // zero weights. Established funds always have a previous-day snapshot, so this is an edge case.
    var bodPositions =
        fundPositionRepository.findByNavDateAndFundAndAccountType(previousDate, fund, SECURITY);
    var bodTotalSecurities =
        bodPositions.stream()
            .map(FundPosition::getMarketValue)
            .filter(Objects::nonNull)
            .reduce(ZERO, BigDecimal::add);
    var bodTotalNav =
        fundPositionRepository.sumMarketValueByFundAndAccountTypes(
            fund, previousDate, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY));

    List<TrackingDifferenceCalculator.BodHolding> bodHoldings = null;
    BigDecimal bodSecuritiesFraction = null;
    if (bodTotalNav != null && bodTotalNav.signum() > 0 && bodTotalSecurities.signum() > 0) {
      bodHoldings =
          buildBodHoldings(fund, checkDate, previousDate, bodPositions, bodTotalSecurities);
      if (bodHoldings != null) {
        bodSecuritiesFraction = bodTotalSecurities.divide(bodTotalNav, SCALE, RoundingMode.HALF_UP);
      }
    } else {
      log.warn(
          "Begin-of-day holdings unavailable for NAV residual, skipping gate: fund={}, checkDate={}, anchorDate={}, bodTotalNav={}, bodTotalSecurities={}",
          fund,
          checkDate,
          previousDate,
          bodTotalNav,
          bodTotalSecurities);
    }

    var priorBreaches = countConsecutiveBreaches(fund, MODEL_PORTFOLIO, checkDate);

    var modelInput =
        TrackingInput.builder()
            .fund(fund)
            .checkDate(checkDate)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(todayNav.value())
            .yesterdayNav(yesterdayNav.value())
            .securities(blendedSecurities)
            .cashWeight(cashWeight)
            .annualFeeRate(annualFeeRate)
            .consecutiveBreachDays(priorBreaches.count())
            .bodHoldings(bodHoldings)
            .bodSecuritiesFraction(bodSecuritiesFraction)
            .build();

    calculator
        .calculate(modelInput)
        .ifPresent(
            result -> {
              var withConsecutive = updateConsecutiveCount(result, priorBreaches);
              saveEvent(withConsecutive);
              results.add(withConsecutive);
            });

    buildBenchmarkCheck(fund, checkDate, previousDate, todayNav, yesterdayNav)
        .ifPresent(
            result -> {
              saveEvent(result);
              results.add(result);
            });

    buildBenchmarkModelCheck(fund, checkDate, securities)
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
    var breach = td.abs().compareTo(calculator.breachThreshold(checkDate)) >= 0;

    var priorBreaches = countConsecutiveBreaches(fund, BENCHMARK, checkDate);
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

  private Optional<TrackingDifferenceResult> buildBenchmarkModelCheck(
      TulevaFund fund, LocalDate checkDate, List<SecurityData> securities) {

    var validSecurities =
        securities.stream()
            .filter(s -> s.today().price() != null && s.previous().price() != null)
            .filter(s -> s.previous().price().signum() != 0)
            .filter(s -> s.today().date() != null && s.previous().date() != null)
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
          lookupReturn(benchmarkKey, s.today().date(), s.previous().date(), maxDailyReturn);
      if (bmReturn.isEmpty()) {
        log.warn(
            "Missing benchmark model data: fund={}, isin={}, benchmarkKey={}",
            fund,
            s.isin(),
            benchmarkKey);
        continue;
      }
      var secReturn =
          calculator.safeDailyReturn(s.today().price(), s.previous().price(), maxDailyReturn);
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

    var priorBreaches = countConsecutiveBreaches(fund, BENCHMARK_MODEL, checkDate);
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
      escalationAttrs = mergeAttributions(priorBreaches.contributionByIsin(), attributions);
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

  private String resolveBenchmarkKey(String isin) {
    var ticker = FundTicker.findByIsin(isin).orElse(null);
    if (ticker == null) {
      return null;
    }

    var category = ticker.getBenchmarkCategory();
    if (category == null) {
      return null;
    }

    var isEtf =
        ticker.getEodhdTicker().endsWith(".XETRA") || ticker.getEodhdTicker().endsWith(".PA.EODHD");

    return switch (category) {
      case EQUITY_DM ->
          isEtf ? ISHARES_CORE_MSCI_WORLD.getXetraStorageKey().orElseThrow() : "MSCI_WORLD";
      case EQUITY_EM -> isEtf ? ISHARES_MSCI_EM.getXetraStorageKey().orElseThrow() : "MSCI_EM";
      case BOND_EURO -> ISHARES_EURO_AGG_BOND_ETF.getXetraStorageKey().orElseThrow();
      case BOND_GLOBAL -> ISHARES_GLOBAL_AGG_BOND_ETF.getXetraStorageKey().orElseThrow();
    };
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

  private List<SecurityData> buildSecurityData(
      TulevaFund fund,
      List<ModelPortfolioAllocation> allocations,
      List<ModelPortfolioAllocation> previousAllocations,
      List<FundPosition> todayPositions,
      BigDecimal totalSecurities,
      LocalDate checkDate,
      LocalDate previousDate) {

    Map<String, FundPosition> todayByIsin =
        todayPositions.stream()
            .filter(p -> p.getAccountId() != null)
            .collect(Collectors.toMap(FundPosition::getAccountId, p -> p, (a, b) -> a));

    var todayCutoff = computePriceCutoff(fund, checkDate);
    var yesterdayCutoff = computePriceCutoff(fund, previousDate);

    var currentIsins =
        allocations.stream()
            .map(ModelPortfolioAllocation::getIsin)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    var result =
        new ArrayList<>(
            allocations.stream()
                .filter(a -> a.getIsin() != null)
                .map(
                    a ->
                        buildOneSecurityData(
                            a.getIsin(),
                            a.getWeight(),
                            todayByIsin,
                            totalSecurities,
                            checkDate,
                            previousDate,
                            todayCutoff,
                            yesterdayCutoff))
                .toList());

    previousAllocations.stream()
        .filter(a -> a.getIsin() != null)
        .filter(a -> !currentIsins.contains(a.getIsin()))
        .filter(a -> todayByIsin.containsKey(a.getIsin()))
        .map(
            a ->
                buildOneSecurityData(
                    a.getIsin(),
                    ZERO,
                    todayByIsin,
                    totalSecurities,
                    checkDate,
                    previousDate,
                    todayCutoff,
                    yesterdayCutoff))
        .forEach(result::add);

    return result;
  }

  private List<SecurityData> blendTransitionWeights(
      List<SecurityData> securities,
      List<ModelPortfolioAllocation> allocations,
      List<ModelPortfolioAllocation> previousAllocations,
      List<FundPosition> positions,
      TulevaFund fund) {

    if (previousAllocations.isEmpty()) {
      return securities;
    }

    var currentIsins =
        allocations.stream()
            .map(ModelPortfolioAllocation::getIsin)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    var previousIsins =
        previousAllocations.stream()
            .map(ModelPortfolioAllocation::getIsin)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    var addedIsins = new HashSet<>(currentIsins);
    addedIsins.removeAll(previousIsins);
    var removedIsins = new HashSet<>(previousIsins);
    removedIsins.removeAll(currentIsins);

    if (addedIsins.isEmpty() && removedIsins.isEmpty()) {
      return securities;
    }

    var positionIsins =
        positions.stream()
            .map(FundPosition::getAccountId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    var knownIsins = new HashSet<>(currentIsins);
    knownIsins.addAll(previousIsins);
    var unexpectedIsins = new HashSet<>(positionIsins);
    unexpectedIsins.removeAll(knownIsins);

    if (!unexpectedIsins.isEmpty()) {
      log.warn(
          "Unexpected ISINs in portfolio, skipping transition blending: fund={}, unexpected={}",
          fund,
          unexpectedIsins);
      return securities;
    }

    var removedAndHeld = new HashSet<>(removedIsins);
    removedAndHeld.retainAll(positionIsins);

    if (removedAndHeld.isEmpty()) {
      return securities;
    }

    var transitionIsins = new HashSet<>(addedIsins);
    transitionIsins.addAll(removedIsins);

    log.info(
        "Transition blending applied: fund={}, removedAndHeld={}, addedIsins={}",
        fund,
        removedAndHeld,
        addedIsins);

    return securities.stream()
        .map(
            s ->
                transitionIsins.contains(s.isin())
                    ? new SecurityData(
                        s.isin(), s.actualWeight(), s.actualWeight(), s.today(), s.previous())
                    : s)
        .toList();
  }

  private SecurityData buildOneSecurityData(
      String isin,
      BigDecimal modelWeight,
      Map<String, FundPosition> todayByIsin,
      BigDecimal totalSecurities,
      LocalDate checkDate,
      LocalDate previousDate,
      Instant todayCutoff,
      Instant yesterdayCutoff) {

    var today = resolvePriceSnapshot(isin, checkDate, todayCutoff);
    var previous = resolvePriceSnapshot(isin, previousDate, yesterdayCutoff);

    var todayPos = todayByIsin.get(isin);
    var actualMarketValue = todayPos != null ? todayPos.getMarketValue() : ZERO;
    var actualWeight =
        totalSecurities.signum() != 0
            ? actualMarketValue.divide(totalSecurities, 6, RoundingMode.HALF_UP)
            : ZERO;

    return new SecurityData(isin, modelWeight, actualWeight, today, previous);
  }

  private PriceSnapshot resolvePriceSnapshot(String isin, LocalDate date, Instant cutoff) {
    var resolved =
        positionPriceResolver
            .resolve(isin, date, cutoff)
            .filter(rp -> rp.validationStatus() == ValidationStatus.OK)
            .orElse(null);
    return new PriceSnapshot(
        resolved != null ? resolved.usedPrice() : null,
        resolved != null ? resolved.priceDate() : null);
  }

  // Begin-of-day holdings = the SECURITY positions the fund actually held entering checkDate, i.e.
  // the previous fund NAV date's (anchor) end-of-day snapshot. Anchored on the FUND NAV date
  // (previousDate), never on a per-instrument or custodian "latest" date — on a model-switch / MOC
  // trade day the instruments themselves may have later prices, but the fund earned its
  // begin-of-day portfolio's return intraday, valued at price(checkDate)/price(anchorDate). Returns
  // null (caller skips the navResidual gate) when any held instrument is missing a price, so the
  // residual is never computed on partial data.
  @Nullable
  private List<TrackingDifferenceCalculator.BodHolding> buildBodHoldings(
      TulevaFund fund,
      LocalDate checkDate,
      LocalDate previousDate,
      List<FundPosition> bodPositions,
      BigDecimal bodTotalSecurities) {
    var todayCutoff = computePriceCutoff(fund, checkDate);
    var anchorCutoff = computePriceCutoff(fund, previousDate);

    var holdings = new ArrayList<TrackingDifferenceCalculator.BodHolding>();
    var missingPrices = new ArrayList<String>();
    for (var pos : bodPositions) {
      var marketValue = pos.getMarketValue();
      if (marketValue == null || marketValue.signum() == 0) {
        continue;
      }
      var isin = pos.getAccountId();
      if (isin == null) {
        // A non-zero securities position with no ISIN counts toward bodTotalSecurities but cannot
        // be
        // priced — the sleeve weights would no longer sum to ~1. Treat as a malformed snapshot and
        // skip the gate rather than compute the residual on partial data.
        log.warn(
            "Begin-of-day securities position with value but no ISIN, skipping NAV residual gate: fund={}, checkDate={}, anchorDate={}, marketValue={}",
            fund,
            checkDate,
            previousDate,
            marketValue);
        return null;
      }
      var weight = marketValue.divide(bodTotalSecurities, SCALE, RoundingMode.HALF_UP);
      var today = resolvePriceSnapshot(isin, checkDate, todayCutoff);
      var previous = resolvePriceSnapshot(isin, previousDate, anchorCutoff);
      if (today.price() == null || previous.price() == null || previous.price().signum() == 0) {
        missingPrices.add(isin);
      }
      holdings.add(new TrackingDifferenceCalculator.BodHolding(isin, weight, today, previous));
    }

    if (!missingPrices.isEmpty()) {
      log.warn(
          "Missing begin-of-day prices for NAV residual, skipping gate: fund={}, checkDate={}, anchorDate={}, missingIsins={}",
          fund,
          checkDate,
          previousDate,
          missingPrices);
      return null;
    }
    return holdings;
  }

  private Instant computePriceCutoff(TulevaFund fund, LocalDate navDate) {
    var calculationDate = publicHolidays.nextWorkingDay(navDate);
    var cutoff = calculationDate.atTime(fund.getNavCutoffTime()).atZone(ESTONIAN_ZONE).toInstant();
    return cutoff.plus(Duration.ofMinutes(5));
  }

  private ConsecutiveBreachInfo countConsecutiveBreaches(
      TulevaFund fund, TrackingCheckType checkType, LocalDate checkDate) {
    try {
      return doCountConsecutiveBreaches(fund, checkType, checkDate);
    } catch (Exception e) {
      log.warn(
          "Escalation count failed, using empty: fund={}, checkType={}, error={}",
          fund,
          checkType,
          e.getMessage());
      return new ConsecutiveBreachInfo(0, ZERO, ZERO, ZERO, Map.of(), ZERO, ZERO, ZERO);
    }
  }

  private ConsecutiveBreachInfo doCountConsecutiveBreaches(
      TulevaFund fund, TrackingCheckType checkType, LocalDate checkDate) {
    int lookback;
    try {
      lookback = calculator.escalationLookbackDays(checkDate);
    } catch (IllegalStateException e) {
      log.warn("Escalation parameters not configured, using fallback: {}", e.getMessage());
      lookback = ESCALATION_LOOKBACK_FALLBACK;
    } catch (Exception e) {
      log.warn("Escalation lookback parameter lookup failed, using fallback: {}", e.getMessage());
      lookback = ESCALATION_LOOKBACK_FALLBACK;
    }
    var recent = eventRepository.findMostRecentEvents(fund, checkType, checkDate, lookback);
    int count = 0;
    var compoundedFund = BigDecimal.ONE;
    var compoundedBenchmark = BigDecimal.ONE;
    var cashDragSum = ZERO;
    var feeDragSum = ZERO;
    var residualSum = ZERO;
    var contributionByIsin = new java.util.LinkedHashMap<String, BigDecimal>();

    for (var event : recent) {
      if (!event.isBreach()) {
        break;
      }
      count++;
      compoundedFund = compoundedFund.multiply(BigDecimal.ONE.add(event.getFundReturn()));
      compoundedBenchmark =
          compoundedBenchmark.multiply(BigDecimal.ONE.add(event.getBenchmarkReturn()));

      try {
        var result = event.getResult();
        cashDragSum = cashDragSum.add(toBd(result.get("cashDrag")));
        feeDragSum = feeDragSum.add(toBd(result.get("feeDrag")));
        residualSum = residualSum.add(toBd(result.get("residual")));

        @SuppressWarnings("unchecked")
        var attrs =
            (List<Map<String, Object>>) result.getOrDefault("securityAttributions", List.of());
        for (var attr : attrs) {
          var isin = (String) attr.get("isin");
          if (isin == null || isin.isBlank()) {
            continue;
          }
          var contribution = toBd(attr.get("contribution"));
          contributionByIsin.merge(isin, contribution, BigDecimal::add);
        }
      } catch (Exception e) {
        log.warn(
            "Failed to parse attribution from event: checkDate={}, error={}",
            event.getCheckDate(),
            e.getMessage());
      }
    }

    var compoundedFundReturn = compoundedFund.subtract(BigDecimal.ONE);
    var compoundedBenchmarkReturn = compoundedBenchmark.subtract(BigDecimal.ONE);
    var compoundedTd = compoundedFundReturn.subtract(compoundedBenchmarkReturn);

    return new ConsecutiveBreachInfo(
        count,
        compoundedTd,
        compoundedFundReturn,
        compoundedBenchmarkReturn,
        contributionByIsin,
        cashDragSum,
        feeDragSum,
        residualSum);
  }

  private static Map<String, BigDecimal> mergeAttributions(
      Map<String, BigDecimal> prior, List<SecurityAttribution> todayAttrs) {
    var merged = new java.util.LinkedHashMap<>(prior);
    if (todayAttrs != null) {
      for (var attr : todayAttrs) {
        if (attr.isin() == null || attr.isin().isBlank()) {
          continue;
        }
        merged.merge(attr.isin(), attr.contribution(), BigDecimal::add);
      }
    }
    return merged;
  }

  private static BigDecimal toBd(Object value) {
    if (value == null) return ZERO;
    if (value instanceof BigDecimal bd) return bd;
    if (value instanceof Number n) return new BigDecimal(n.toString());
    if (value instanceof String s && !s.isBlank()) {
      try {
        return new BigDecimal(s);
      } catch (NumberFormatException e) {
        log.warn("Unparseable BigDecimal in JSONB: value={}", s);
        return ZERO;
      }
    }
    if (!(value instanceof String)) {
      log.warn("Unexpected type in JSONB numeric field: type={}", value.getClass().getSimpleName());
    }
    return ZERO;
  }

  private TrackingDifferenceResult updateConsecutiveCount(
      TrackingDifferenceResult result, ConsecutiveBreachInfo priorBreaches) {
    if (!result.breach()) {
      return result.toBuilder().consecutiveBreachDays(0).consecutiveNetTd(ZERO).build();
    }
    int days = priorBreaches.count() + 1;

    var compoundedFund =
        BigDecimal.ONE
            .add(priorBreaches.compoundedFundReturn())
            .multiply(BigDecimal.ONE.add(result.fundReturn()))
            .subtract(BigDecimal.ONE);
    var compoundedBenchmark =
        BigDecimal.ONE
            .add(priorBreaches.compoundedBenchmarkReturn())
            .multiply(BigDecimal.ONE.add(result.benchmarkReturn()))
            .subtract(BigDecimal.ONE);
    var compoundedTd = compoundedFund.subtract(compoundedBenchmark);

    return result.toBuilder()
        .consecutiveBreachDays(days)
        .consecutiveNetTd(compoundedTd)
        .compoundedFundReturn(compoundedFund)
        .compoundedBenchmarkReturn(compoundedBenchmark)
        .escalationAttributions(
            mergeAttributions(priorBreaches.contributionByIsin(), result.securityAttributions()))
        .escalationCashDrag(
            priorBreaches.cashDragSum().add(result.cashDrag() != null ? result.cashDrag() : ZERO))
        .escalationFeeDrag(
            priorBreaches.feeDragSum().add(result.feeDrag() != null ? result.feeDrag() : ZERO))
        .escalationResidual(
            priorBreaches.residualSum().add(result.residual() != null ? result.residual() : ZERO))
        .build();
  }

  record ConsecutiveBreachInfo(
      int count,
      BigDecimal compoundedTd,
      BigDecimal compoundedFundReturn,
      BigDecimal compoundedBenchmarkReturn,
      Map<String, BigDecimal> contributionByIsin,
      BigDecimal cashDragSum,
      BigDecimal feeDragSum,
      BigDecimal residualSum) {}

  private void saveEvent(TrackingDifferenceResult result) {
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
                a -> {
                  var map = new java.util.LinkedHashMap<String, Object>();
                  map.put("isin", a.isin());
                  map.put("modelWeight", Objects.requireNonNullElse(a.modelWeight(), ZERO));
                  map.put("actualWeight", Objects.requireNonNullElse(a.actualWeight(), ZERO));
                  map.put(
                      "weightDifference", Objects.requireNonNullElse(a.weightDifference(), ZERO));
                  map.put("securityReturn", a.securityReturn());
                  map.put("benchmarkReturn", Objects.requireNonNullElse(a.benchmarkReturn(), ZERO));
                  map.put("contribution", a.contribution());
                  return map;
                })
            .toList();

    return Map.of(
        "securityAttributions",
        attributions,
        "cashDrag",
        Objects.requireNonNullElse(result.cashDrag(), ZERO),
        "feeDrag",
        Objects.requireNonNullElse(result.feeDrag(), ZERO),
        "residual",
        Objects.requireNonNullElse(result.residual(), ZERO),
        "impliedFundReturn",
        Objects.requireNonNullElse(result.impliedFundReturn(), ZERO),
        "navResidual",
        Objects.requireNonNullElse(result.navResidual(), ZERO),
        "navResidualBreach",
        result.navResidualBreach(),
        // Preserve the not-evaluated sentinel in history: a persisted impliedFundReturn of 0 with
        // navResidualEvaluated=false means the begin-of-day gate was skipped, not a real zero.
        "navResidualEvaluated",
        result.impliedFundReturn() != null);
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

  static class IncompletePriceDataException extends RuntimeException {

    private final transient List<TrackingDifferenceResult> completedResults;

    IncompletePriceDataException(String message, List<TrackingDifferenceResult> completedResults) {
      super(message);
      this.completedResults = completedResults;
    }

    List<TrackingDifferenceResult> completedResults() {
      return completedResults;
    }
  }
}
