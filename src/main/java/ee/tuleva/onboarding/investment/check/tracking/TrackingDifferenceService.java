package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static java.math.BigDecimal.ZERO;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.SecurityData;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceCalculator.TrackingInput;
import ee.tuleva.onboarding.investment.fees.FeeAccrual;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class TrackingDifferenceService {

  private static final int SCALE = 6;

  private final Clock clock;
  private final FundPositionRepository fundPositionRepository;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final PublicHolidays publicHolidays;
  private final FeeAccrualRepository feeAccrualRepository;
  private final FeeChargedToFundPolicy feeChargedToFundPolicy;
  private final TrackingDifferenceEventRepository eventRepository;
  private final TrackingDifferenceCalculator calculator;
  private final FundNavQueryService fundNavQueryService;
  private final SecurityDataBuilder securityDataBuilder;
  private final ConsecutiveBreachTracker consecutiveBreachTracker;
  private final BenchmarkCheckBuilder benchmarkCheckBuilder;

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

    var previousDate = publicHolidays.previousWorkingDay(checkDate);
    var todayValue = fundNavQueryService.findLatestNavPerUnit(fund.getCode(), checkDate);
    var yesterdayValue = fundNavQueryService.findLatestNavPerUnit(fund.getCode(), previousDate);

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

    var totalSecurities =
        positions.stream()
            .map(FundPosition::getMarketValue)
            .filter(Objects::nonNull)
            .reduce(ZERO, BigDecimal::add);

    var securities =
        securityDataBuilder.buildSecurityData(
            fund,
            allocations,
            previousAllocations,
            positions,
            totalSecurities,
            checkDate,
            previousDate);

    // Gate on the blended weights, not the raw model. An instrument being switched into carries
    // its actual weight of zero until the fund buys it, so it needs no price of ours; an
    // instrument being switched out of still carries its full holding, so it does. Either way a
    // weight we cannot price means the model return for the day is unknowable - the check has to
    // say so rather than quietly leave that weight out of the benchmark.
    //
    // A zero anchor price is not a price either: nothing can be divided by it, so the calculator
    // drops the instrument and its model weight silently leaves the benchmark return.
    var blendedSecurities =
        securityDataBuilder.blendTransitionWeights(
            securities, allocations, previousAllocations, positions, fund);

    var missingPrices =
        blendedSecurities.stream()
            .filter(s -> s.modelWeight().signum() > 0)
            .filter(
                s ->
                    s.today().price() == null
                        || s.previous().price() == null
                        || s.previous().price().signum() == 0)
            .map(SecurityData::isin)
            .toList();
    if (!missingPrices.isEmpty()) {
      throw new IncompletePriceDataException(
          "fund=%s, checkDate=%s, missingIsins=%s".formatted(fund, checkDate, missingPrices),
          List.of());
    }

    var misalignedSecurities =
        blendedSecurities.stream()
            .filter(s -> s.modelWeight().signum() > 0)
            .filter(
                s ->
                    SecurityDataBuilder.isStaleDate(s.today(), checkDate)
                        || SecurityDataBuilder.isStaleDate(s.previous(), previousDate))
            .map(
                s ->
                    "%s[today=%s, prev=%s]"
                        .formatted(s.isin(), s.today().date(), s.previous().date()))
            .toList();
    if (!misalignedSecurities.isEmpty()) {
      log.warn(
          "Benchmark price dates misaligned (may inflate MODEL_PORTFOLIO TD, no behavior change): fund={}, checkDate={}, previousDate={}, securities={}",
          fund,
          checkDate,
          previousDate,
          misalignedSecurities);
    }

    var bodPositions =
        fundPositionRepository.findByNavDateAndFundAndAccountType(previousDate, fund, SECURITY);
    var bodTotalSecurities =
        bodPositions.stream()
            .map(FundPosition::getMarketValue)
            .filter(Objects::nonNull)
            .reduce(ZERO, BigDecimal::add);
    var previousTotalNav =
        fundPositionRepository.sumMarketValueByFundAndAccountTypes(
            fund, previousDate, List.of(SECURITY, CASH, RECEIVABLES, LIABILITY));

    List<TrackingDifferenceCalculator.BodHolding> bodHoldings = null;
    BigDecimal bodSecuritiesFraction = null;
    if (previousTotalNav != null
        && previousTotalNav.signum() > 0
        && bodTotalSecurities.signum() > 0) {
      bodHoldings =
          securityDataBuilder.buildBodHoldings(
              fund, checkDate, previousDate, bodPositions, bodTotalSecurities);
      if (bodHoldings != null) {
        bodSecuritiesFraction =
            bodTotalSecurities.divide(previousTotalNav, SCALE, RoundingMode.HALF_UP);
      }
    } else {
      log.warn(
          "Begin-of-day holdings unavailable for NAV residual, skipping gate: fund={}, checkDate={}, anchorDate={}, bodTotalNav={}, bodTotalSecurities={}",
          fund,
          checkDate,
          previousDate,
          previousTotalNav,
          bodTotalSecurities);
    }

    var accruedFeeFraction =
        accruedFeeFraction(fund, previousDate, checkDate, previousTotalNav, totalNav);

    var priorBreaches =
        consecutiveBreachTracker.countConsecutiveBreaches(fund, MODEL_PORTFOLIO, checkDate);

    var modelInput =
        TrackingInput.builder()
            .fund(fund)
            .checkDate(checkDate)
            .checkType(MODEL_PORTFOLIO)
            .todayNav(todayNav.value())
            .yesterdayNav(yesterdayNav.value())
            .securities(blendedSecurities)
            .cashWeight(cashWeight)
            .accruedFeeFraction(accruedFeeFraction)
            .consecutiveBreachDays(priorBreaches.count())
            .bodHoldings(bodHoldings)
            .bodSecuritiesFraction(bodSecuritiesFraction)
            .openingNetAssets(previousTotalNav)
            .closingNetAssets(totalNav)
            .previousUnits(unitsOutstanding(fund, previousDate))
            .todayUnits(unitsOutstanding(fund, checkDate))
            .securityQuantitiesChanged(quantitiesChanged(bodPositions, positions))
            .build();

    calculator
        .calculate(modelInput)
        .ifPresent(
            result -> {
              var withConsecutive =
                  consecutiveBreachTracker.updateConsecutiveCount(result, priorBreaches);
              saveEvent(withConsecutive);
              results.add(withConsecutive);
            });

    benchmarkCheckBuilder
        .buildBenchmarkCheck(fund, checkDate, previousDate, todayNav, yesterdayNav)
        .ifPresent(
            result -> {
              saveEvent(result);
              results.add(result);
            });

    benchmarkCheckBuilder
        .buildBenchmarkModelCheck(fund, checkDate, securities)
        .ifPresent(
            result -> {
              saveEvent(result);
              results.add(result);
            });

    return results;
  }

  private BigDecimal accruedFeeFraction(
      TulevaFund fund,
      LocalDate previousDate,
      LocalDate checkDate,
      @Nullable BigDecimal previousTotalNav,
      BigDecimal totalNav) {
    var base = feeFractionBase(fund, previousDate, checkDate, previousTotalNav, totalNav);
    if (base.signum() <= 0) {
      return ZERO;
    }
    var accruals =
        feeAccrualRepository.findByFundAndDateRange(fund, previousDate.plusDays(1), checkDate);
    var chargedResolvers =
        accruals.stream()
            .map(FeeAccrual::feeType)
            .distinct()
            .collect(
                toMap(identity(), feeType -> feeChargedToFundPolicy.resolverFor(fund, feeType)));
    var chargedAccruals =
        accruals.stream()
            .filter(
                a ->
                    Objects.requireNonNull(
                            chargedResolvers.get(a.feeType()),
                            "No charged-to-fund resolver: feeType=" + a.feeType())
                        .chargedOn(a.accrualDate()))
            .toList();
    warnOnIncompleteAccrualCoverage(
        fund, previousDate, checkDate, chargedResolvers, chargedAccruals);
    var accrued =
        chargedAccruals.stream().map(FeeAccrual::dailyAmountGross).reduce(ZERO, BigDecimal::add);
    return accrued.divide(base, SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal feeFractionBase(
      TulevaFund fund,
      LocalDate previousDate,
      LocalDate checkDate,
      @Nullable BigDecimal previousTotalNav,
      BigDecimal totalNav) {
    if (previousTotalNav != null && previousTotalNav.signum() > 0) {
      return previousTotalNav;
    }
    log.warn(
        "No positions on the previous NAV date, dividing the fee accrual by the check date NAV instead: fund={}, previousDate={}, checkDate={}",
        fund,
        previousDate,
        checkDate);
    return totalNav;
  }

  private void warnOnIncompleteAccrualCoverage(
      TulevaFund fund,
      LocalDate previousDate,
      LocalDate checkDate,
      Map<FeeType, FeeChargedToFundPolicy.Resolver> chargedResolvers,
      List<FeeAccrual> chargedAccruals) {
    if (chargedResolvers.isEmpty()) {
      log.warn(
          "No fee accruals for the check window, the uncovered fee will surface as tracking residual: fund={}, previousDate={}, checkDate={}, windowDays={}",
          fund,
          previousDate,
          checkDate,
          windowDates(previousDate, checkDate).size());
      return;
    }
    var coveredDatesByFeeType =
        chargedAccruals.stream()
            .collect(groupingBy(FeeAccrual::feeType, mapping(FeeAccrual::accrualDate, toSet())));
    chargedResolvers.forEach(
        (feeType, resolver) ->
            warnOnIncompleteFeeTypeCoverage(
                fund,
                previousDate,
                checkDate,
                feeType,
                resolver,
                coveredDatesByFeeType.getOrDefault(feeType, Set.of()).size()));
  }

  private void warnOnIncompleteFeeTypeCoverage(
      TulevaFund fund,
      LocalDate previousDate,
      LocalDate checkDate,
      FeeType feeType,
      FeeChargedToFundPolicy.Resolver resolver,
      int coveredDays) {
    var chargedDays = 0;
    for (var date : windowDates(previousDate, checkDate)) {
      try {
        if (resolver.chargedOn(date)) {
          chargedDays++;
        }
      } catch (IllegalStateException e) {
        log.warn(
            "Fee accrual coverage undetermined, the fee policy does not resolve for a day of the check window: fund={}, feeType={}, previousDate={}, checkDate={}, unresolvedDate={}",
            fund,
            feeType,
            previousDate,
            checkDate,
            date,
            e);
        return;
      }
    }
    if (coveredDays < chargedDays) {
      log.warn(
          "Fee accruals incomplete for the check window, the uncovered fee will surface as tracking residual: fund={}, feeType={}, previousDate={}, checkDate={}, chargedDays={}, coveredDays={}",
          fund,
          feeType,
          previousDate,
          checkDate,
          chargedDays,
          coveredDays);
    }
  }

  private boolean quantitiesChanged(
      List<FundPosition> bodPositions, List<FundPosition> todayPositions) {
    var bod = quantitiesByIsin(bodPositions);
    var today = quantitiesByIsin(todayPositions);
    if (!bod.keySet().equals(today.keySet())) {
      return true;
    }
    return bod.entrySet().stream()
        .anyMatch(entry -> entry.getValue().compareTo(today.get(entry.getKey())) != 0);
  }

  private Map<String, BigDecimal> quantitiesByIsin(List<FundPosition> positions) {
    return positions.stream()
        .filter(position -> position.getAccountId() != null && position.getQuantity() != null)
        .collect(toMap(FundPosition::getAccountId, FundPosition::getQuantity, BigDecimal::add));
  }

  private @Nullable BigDecimal unitsOutstanding(TulevaFund fund, LocalDate navDate) {
    return fundPositionRepository.findByNavDateAndFundAndAccountType(navDate, fund, UNITS).stream()
        .map(FundPosition::getQuantity)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private List<LocalDate> windowDates(LocalDate previousDate, LocalDate checkDate) {
    return previousDate.plusDays(1).datesUntil(checkDate.plusDays(1)).toList();
  }

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
            .result(TrackingDifferenceEventMapper.buildResultMap(result))
            .build();

    eventRepository.save(event);
  }

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
