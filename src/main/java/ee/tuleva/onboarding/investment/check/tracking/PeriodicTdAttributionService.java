package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.check.tracking.TdAttributionCalculator.DailyRecord;
import ee.tuleva.onboarding.investment.check.tracking.TdAttributionCalculator.SecurityDailyData;
import ee.tuleva.onboarding.investment.check.tracking.TdAttributionCalculator.TdAttributionInput;
import ee.tuleva.onboarding.investment.fees.FeeAccrual;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.fees.InstrumentFee;
import ee.tuleva.onboarding.investment.fees.InstrumentFeeRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class PeriodicTdAttributionService {

  private static final int SCALE = TdAttributionCalculator.SCALE;
  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private final TrackingDifferenceEventRepository tdEventRepository;
  private final FeeAccrualRepository feeAccrualRepository;
  private final FeeRateRepository feeRateRepository;
  private final FeeChargedToFundPolicy feeChargedToFundPolicy;
  private final FundPositionRepository fundPositionRepository;
  private final FundNavQueryService fundNavQueryService;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final PeriodicTdAttributionRepository attributionRepository;
  private final TransactionExecutionRepository transactionExecutionRepository;
  private final InstrumentFeeRepository instrumentFeeRepository;
  private final PlatformTransactionManager transactionManager;
  private final PublicHolidays publicHolidays;

  private final TdAttributionCalculator calculator = new TdAttributionCalculator();

  public TdAttributionResult computeAttribution(
      TulevaFund fund, LocalDate periodStart, LocalDate periodEnd, PeriodType periodType) {

    var input = buildInput(fund, periodStart, periodEnd, periodType);
    var result = calculator.calculate(input);
    var entity = toEntity(result);

    replaceAttributionRowInSingleTransaction(fund, periodStart, periodEnd, periodType, entity);

    log.info(
        "TD attribution computed: fund={}, period={}-{}, td={}bps, residual={}bps",
        fund,
        periodStart,
        periodEnd,
        result.tdGeometric().multiply(BigDecimal.valueOf(10000)).setScale(1, HALF_UP),
        result.residual().multiply(BigDecimal.valueOf(10000)).setScale(1, HALF_UP));

    return result;
  }

  private void replaceAttributionRowInSingleTransaction(
      TulevaFund fund,
      LocalDate periodStart,
      LocalDate periodEnd,
      PeriodType periodType,
      PeriodicTdAttribution entity) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              attributionRepository.deleteByFundAndPeriodStartAndPeriodEndAndPeriodType(
                  fund, periodStart, periodEnd, periodType);
              attributionRepository.save(entity);
            });
  }

  public void computeForAllFunds(
      LocalDate periodStart, LocalDate periodEnd, PeriodType periodType) {
    for (var fund : TulevaFund.values()) {
      try {
        computeAttribution(fund, periodStart, periodEnd, periodType);
      } catch (Exception e) {
        log.error(
            "Failed to compute TD attribution: fund={}, period={}-{}",
            fund,
            periodStart,
            periodEnd,
            e);
      }
    }
  }

  public void backfillMonths(int monthsBack, Clock clock) {
    var today = LocalDate.now(clock);
    for (int i = monthsBack; i >= 1; i--) {
      var month = YearMonth.from(today).minusMonths(i);
      log.info("Backfilling TD attribution: period={}", month);
      computeForAllFunds(month.atDay(1), month.atEndOfMonth(), PeriodType.MONTHLY);
    }
  }

  private TdAttributionInput buildInput(
      TulevaFund fund, LocalDate periodStart, LocalDate periodEnd, PeriodType periodType) {

    var tdEvents =
        tdEventRepository.findDeduplicatedEventsForPeriod(
            fund, MODEL_PORTFOLIO, periodStart, periodEnd);

    var feeAccruals = feeAccrualRepository.findByFundAndDateRange(fund, periodStart, periodEnd);

    var modelAllocations =
        modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
            fund, periodStart, periodEnd);

    var seriesGapDays =
        countSeriesGaps(
            tdEvents.stream().map(TrackingDifferenceEvent::getCheckDate).toList(), publicHolidays);
    if (seriesGapDays > 0) {
      log.warn(
          "Gaps in daily TD series may distort geometric compounding: fund={}, period={}-{}, gapDays={}",
          fund,
          periodStart,
          periodEnd,
          seriesGapDays);
    }

    var dailyRecords = buildDailyRecords(fund, tdEvents, modelAllocations);

    var mgmtFeeDragTotal = computeFeeDragPeriod(fund, feeAccruals, FeeType.MANAGEMENT);
    var depotFeeDragTotal = computeFeeDragPeriod(fund, feeAccruals, FeeType.DEPOT);

    var avgAum = computeAvgAumFromDailyRecords(dailyRecords);
    var mgmtFeeDragReturn =
        avgAum.signum() > 0 ? mgmtFeeDragTotal.negate().divide(avgAum, SCALE, HALF_UP) : ZERO;
    var depotFeeDragReturn =
        avgAum.signum() > 0 ? depotFeeDragTotal.negate().divide(avgAum, SCALE, HALF_UP) : ZERO;

    var calendarDays = (int) ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
    var expectedAnnualFeeRate =
        feeRateRepository
            .findValidRate(fund, FeeType.MANAGEMENT, periodEnd)
            .map(r -> r.annualRate())
            .orElse(ZERO);

    var txnCosts =
        transactionExecutionRepository.sumCommissionsForFundAndPeriod(
            fund.getCode(),
            periodStart.atStartOfDay(ESTONIAN_ZONE).toInstant(),
            periodEnd.plusDays(1).atStartOfDay(ESTONIAN_ZONE).toInstant());
    var txnCostReturn =
        avgAum.signum() > 0 ? txnCosts.negate().divide(avgAum, SCALE, HALF_UP) : ZERO;

    var bmModelEvents =
        tdEventRepository.findDeduplicatedEventsForPeriod(
            fund, BENCHMARK_MODEL, periodStart, periodEnd);
    var etfLayer = computeEtfLayer(fund, bmModelEvents, modelAllocations, periodStart, periodEnd);

    return TdAttributionInput.builder()
        .fund(fund)
        .periodStart(periodStart)
        .periodEnd(periodEnd)
        .periodType(periodType)
        .calendarDays(calendarDays)
        .mgmtFeeDragPeriod(mgmtFeeDragReturn)
        .depotFeeDragPeriod(depotFeeDragReturn)
        .transactionCostsPeriod(txnCostReturn)
        .etfOcfDragPeriod(etfLayer.ocfDrag())
        .benchmarkModelSumPeriod(etfLayer.measuredSum())
        .benchmarkProxyOcfDragPeriod(etfLayer.proxyOcfDrag())
        .expectedAnnualFeeRate(expectedAnnualFeeRate)
        .seriesGapDays(seriesGapDays)
        .etfLayerCoveredDays(etfLayer.coveredDays())
        .etfLayerUnbenchmarkedWeight(etfLayer.unbenchmarkedWeight())
        .etfLayerUnrestoredProxyWeight(etfLayer.unrestoredProxyWeight())
        .dailyRecords(dailyRecords)
        .build();
  }

  static int countSeriesGaps(List<LocalDate> sortedEventDates, PublicHolidays publicHolidays) {
    int gaps = 0;
    for (int i = 1; i < sortedEventDates.size(); i++) {
      var previousEventDate = sortedEventDates.get(i - 1);
      var currentEventDate = sortedEventDates.get(i);
      if (!publicHolidays.previousWorkingDay(currentEventDate).equals(previousEventDate)) {
        gaps++;
      }
    }
    return gaps;
  }

  public void computeQuarterly(TulevaFund fund, int year, int quarter) {
    var start = LocalDate.of(year, (quarter - 1) * 3 + 1, 1);
    var end = start.plusMonths(3).minusDays(1);
    computeAttribution(fund, start, end, PeriodType.QUARTERLY);
  }

  public void computeAnnual(TulevaFund fund, int year) {
    computeAttribution(
        fund, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), PeriodType.ANNUAL);
  }

  private record EtfLayer(
      @Nullable BigDecimal measuredSum,
      BigDecimal ocfDrag,
      BigDecimal proxyOcfDrag,
      int coveredDays,
      BigDecimal unbenchmarkedWeight,
      BigDecimal unrestoredProxyWeight) {

    static EtfLayer unmeasured() {
      return new EtfLayer(null, ZERO, ZERO, 0, ZERO, ZERO);
    }
  }

  private EtfLayer computeEtfLayer(
      TulevaFund fund,
      List<TrackingDifferenceEvent> bmModelEvents,
      List<ModelPortfolioAllocation> modelAllocations,
      LocalDate periodStart,
      LocalDate periodEnd) {

    if (bmModelEvents.isEmpty()) {
      log.warn(
          "No BENCHMARK_MODEL events in the period, reporting the ETF layer as unmeasured rather than as a residual: fund={}, period={}-{}",
          fund,
          periodStart,
          periodEnd);
      return EtfLayer.unmeasured();
    }

    var measuredSum =
        bmModelEvents.stream()
            .map(TrackingDifferenceEvent::getTrackingDifference)
            .reduce(ZERO, BigDecimal::add);

    var coveredDays =
        bmModelEvents.stream()
            .map(TrackingDifferenceEvent::getCheckDate)
            .mapToInt(d -> (int) ChronoUnit.DAYS.between(publicHolidays.previousWorkingDay(d), d))
            .sum();
    var tilingDays = workingDayTilingDays(periodStart, periodEnd);
    if (coveredDays != tilingDays) {
      log.warn(
          "The ETF layer does not tile the period, its OCF term is scaled to the measured days: fund={}, period={}-{}, coveredDays={}, tilingDays={}",
          fund,
          periodStart,
          periodEnd,
          coveredDays,
          tilingDays);
    }

    var allocations = latestAllocations(modelAllocations, periodEnd);
    var measuredIsins = measuredIsins(fund, periodStart, periodEnd, bmModelEvents);
    var rateByIsin =
        instrumentFeeRepository.findAllValidRates(periodEnd).stream()
            .collect(Collectors.toMap(InstrumentFee::isin, InstrumentFee::netOcf, (a, b) -> a));

    var heldOcf = ZERO;
    var proxyOcf = ZERO;
    var unbenchmarkedWeight = ZERO;
    var unrestoredProxyWeight = ZERO;
    var unpricedIsins = new LinkedHashSet<String>();
    var unpricedProxyIsins = new LinkedHashSet<String>();

    for (var allocation : allocations) {
      var weight = allocation.getWeight();
      var isin = allocation.getIsin();

      if (!measuredIsins.contains(isin)) {
        unbenchmarkedWeight = unbenchmarkedWeight.add(weight);
        continue;
      }

      var ocf = rateByIsin.get(isin);
      if (ocf == null) {
        unpricedIsins.add(isin);
      } else {
        heldOcf = heldOcf.add(weight.multiply(ocf));
      }

      var leg = BenchmarkLegResolver.resolve(isin).orElse(null);
      if (leg == null || leg.isIndex()) {
        continue;
      }
      var proxyIsin = leg.proxyEtf().getIsin();
      var proxyRate = rateByIsin.get(proxyIsin);
      if (proxyRate == null) {
        unpricedProxyIsins.add(proxyIsin);
        unrestoredProxyWeight = unrestoredProxyWeight.add(weight);
        continue;
      }
      proxyOcf = proxyOcf.add(weight.multiply(proxyRate));
    }

    if (!unpricedIsins.isEmpty()) {
      log.warn(
          "No OCF rate for model instruments, their cost is missing from etf_ocf_drag and falls into the residual: fund={}, asOf={}, isins={}",
          fund,
          periodEnd,
          unpricedIsins);
    }
    if (!unpricedProxyIsins.isEmpty()) {
      log.warn(
          "No OCF rate for benchmark proxy ETFs, so td_vs_benchmark measures against the proxies rather than the index for their share: fund={}, asOf={}, isins={}",
          fund,
          periodEnd,
          unpricedProxyIsins);
    }
    if (unbenchmarkedWeight.signum() > 0) {
      log.warn(
          "Model weight outside the measured ETF layer, its OCF drag and tracking residual use different weight bases: fund={}, asOf={}, unbenchmarkedWeight={}",
          fund,
          periodEnd,
          unbenchmarkedWeight);
    }

    return new EtfLayer(
        measuredSum,
        annualisedDrag(heldOcf, coveredDays),
        annualisedDrag(proxyOcf, coveredDays),
        coveredDays,
        unbenchmarkedWeight,
        unrestoredProxyWeight);
  }

  private Set<String> measuredIsins(
      TulevaFund fund,
      LocalDate periodStart,
      LocalDate periodEnd,
      List<TrackingDifferenceEvent> bmModelEvents) {
    var datesWithoutAttributions =
        bmModelEvents.stream()
            .filter(event -> attributionIsins(event).isEmpty())
            .map(TrackingDifferenceEvent::getCheckDate)
            .toList();
    if (!datesWithoutAttributions.isEmpty()) {
      log.warn(
          "BENCHMARK_MODEL events without security attributions, leaving the ETF OCF term uncomputed: fund={}, period={}-{}, checkDates={}",
          fund,
          periodStart,
          periodEnd,
          datesWithoutAttributions);
      return Set.of();
    }
    return bmModelEvents.stream()
        .flatMap(event -> attributionIsins(event).stream())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @SuppressWarnings("unchecked")
  private List<String> attributionIsins(TrackingDifferenceEvent event) {
    var attributions =
        (List<Map<String, Object>>)
            event.getResult().getOrDefault("securityAttributions", List.of());
    return attributions.stream().map(attribution -> (String) attribution.get("isin")).toList();
  }

  private int workingDayTilingDays(LocalDate periodStart, LocalDate periodEnd) {
    var firstWorkingDay =
        publicHolidays.isWorkingDay(periodStart)
            ? periodStart
            : publicHolidays.nextWorkingDay(periodStart);
    var lastWorkingDay =
        publicHolidays.isWorkingDay(periodEnd)
            ? periodEnd
            : publicHolidays.previousWorkingDay(periodEnd);
    if (firstWorkingDay.isAfter(lastWorkingDay)) {
      return 0;
    }
    return (int)
        ChronoUnit.DAYS.between(publicHolidays.previousWorkingDay(firstWorkingDay), lastWorkingDay);
  }

  private BigDecimal annualisedDrag(BigDecimal weightedRate, int days) {
    return weightedRate
        .negate()
        .multiply(BigDecimal.valueOf(days))
        .divide(BigDecimal.valueOf(365), SCALE, HALF_UP);
  }

  private List<ModelPortfolioAllocation> latestAllocations(
      List<ModelPortfolioAllocation> allocations, LocalDate asOf) {
    var latestDate =
        allocations.stream()
            .map(ModelPortfolioAllocation::getEffectiveDate)
            .filter(d -> !d.isAfter(asOf))
            .max(LocalDate::compareTo)
            .orElse(null);
    if (latestDate == null) {
      return List.of();
    }
    return allocations.stream().filter(a -> a.getEffectiveDate().equals(latestDate)).toList();
  }

  private BigDecimal computeFeeDragPeriod(
      TulevaFund fund, List<FeeAccrual> accruals, FeeType feeType) {
    var charged = feeChargedToFundPolicy.resolverFor(fund, feeType);
    return accruals.stream()
        .filter(a -> a.feeType() == feeType)
        .filter(a -> charged.chargedOn(a.accrualDate()))
        .map(FeeAccrual::dailyAmountGross)
        .reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal computeAvgAumFromDailyRecords(List<DailyRecord> records) {
    var totalAum = ZERO;
    int count = 0;
    for (var record : records) {
      if (record.aum().signum() > 0) {
        totalAum = totalAum.add(record.aum());
        count++;
      }
    }
    return count > 0 ? totalAum.divide(BigDecimal.valueOf(count), 2, HALF_UP) : ZERO;
  }

  private List<DailyRecord> buildDailyRecords(
      TulevaFund fund,
      List<TrackingDifferenceEvent> tdEvents,
      List<ModelPortfolioAllocation> modelAllocations) {

    var records = new ArrayList<DailyRecord>();

    for (var event : tdEvents) {
      var date = event.getCheckDate();

      var navComponents = loadNavComponents(fund, date);
      if (navComponents == null) {
        log.warn("Missing nav_report data for daily record: fund={}, date={}", fund, date);
        records.add(
            DailyRecord.builder()
                .date(date)
                .fundReturn(event.getFundReturn())
                .modelReturn(event.getBenchmarkReturn())
                .aum(ZERO)
                .cashValue(ZERO)
                .nonSecurityValue(ZERO)
                .securities(List.of())
                .build());
        continue;
      }

      var securityDailyData =
          buildSecurityDailyData(fund, event, modelAllocations, date, navComponents);

      records.add(
          DailyRecord.builder()
              .date(date)
              .fundReturn(event.getFundReturn())
              .modelReturn(event.getBenchmarkReturn())
              .aum(navComponents.aum())
              .cashValue(navComponents.cash())
              .nonSecurityValue(navComponents.nonSecurityValue())
              .securities(securityDailyData)
              .build());
    }

    return records;
  }

  private NavComponents loadNavComponents(TulevaFund fund, LocalDate date) {
    var aum = fundNavQueryService.findAum(fund.getCode(), date);
    if (aum == null || aum.signum() <= 0) {
      return null;
    }
    var securities = fundNavQueryService.findSecuritiesTotalValue(fund.getCode(), date);
    var cash = fundNavQueryService.findCashValue(fund.getCode(), date);
    var negativeFeeAccrualLiabilities =
        fundNavQueryService.findFeeAccrualLiabilities(fund.getCode(), date);

    var nonSecurityValue =
        aum.subtract(securities).subtract(cash).subtract(negativeFeeAccrualLiabilities);

    return new NavComponents(aum, securities, cash, nonSecurityValue);
  }

  private record NavComponents(
      BigDecimal aum, BigDecimal securities, BigDecimal cash, BigDecimal nonSecurityValue) {}

  @SuppressWarnings("unchecked")
  private List<SecurityDailyData> buildSecurityDailyData(
      TulevaFund fund,
      TrackingDifferenceEvent event,
      List<ModelPortfolioAllocation> modelAllocations,
      LocalDate date,
      NavComponents navComponents) {

    var result = event.getResult();
    var attributions =
        (List<Map<String, Object>>) result.getOrDefault("securityAttributions", List.of());
    if (attributions.isEmpty()) {
      return List.of();
    }

    var totalSecurityValue = navComponents.securities();
    if (totalSecurityValue.signum() <= 0) {
      return List.of();
    }

    var positions = fundPositionRepository.findByNavDateAndFundAndAccountType(date, fund, SECURITY);
    var positionByIsin =
        positions.stream()
            .filter(p -> p.getAccountId() != null)
            .collect(Collectors.toMap(FundPosition::getAccountId, p -> p, (a, b) -> a));

    var currentWeights = getModelWeightsForDate(modelAllocations, date);
    var previousWeights = getPreviousModelWeightsForDate(modelAllocations, date);
    var transitionIsins =
        findTransitionIsins(currentWeights, previousWeights, positionByIsin.keySet(), fund);

    var securityDataList = new ArrayList<SecurityDailyData>();

    for (var attr : attributions) {
      var isin = (String) attr.get("isin");
      var securityReturn = toBigDecimal(attr.get("securityReturn"));

      var position = positionByIsin.get(isin);
      var actualMv = position != null ? position.getMarketValue() : ZERO;
      var normalizedActualWeight = actualMv.divide(totalSecurityValue, SCALE, HALF_UP);

      var modelWeight =
          transitionIsins.contains(isin)
              ? normalizedActualWeight
              : currentWeights.getOrDefault(isin, ZERO);
      var normalizedWeightDiff = normalizedActualWeight.subtract(modelWeight);

      securityDataList.add(
          SecurityDailyData.builder()
              .isin(isin)
              .instrumentName(position != null ? position.getAccountName() : null)
              .modelWeight(modelWeight)
              .actualWeight(normalizedActualWeight)
              .normalizedWeightDiff(normalizedWeightDiff)
              .securityReturn(securityReturn)
              .build());
    }

    return securityDataList;
  }

  private Map<String, BigDecimal> getModelWeightsForDate(
      List<ModelPortfolioAllocation> allVersions, LocalDate date) {
    var activeAllocations =
        allVersions.stream().filter(a -> !a.getEffectiveDate().isAfter(date)).toList();
    if (activeAllocations.isEmpty()) {
      return Map.of();
    }
    var latestDate =
        activeAllocations.stream()
            .map(ModelPortfolioAllocation::getEffectiveDate)
            .max(LocalDate::compareTo)
            .orElseThrow();
    return activeAllocations.stream()
        .filter(a -> a.getEffectiveDate().equals(latestDate))
        .collect(
            Collectors.toMap(
                ModelPortfolioAllocation::getIsin, ModelPortfolioAllocation::getWeight));
  }

  private Map<String, BigDecimal> getPreviousModelWeightsForDate(
      List<ModelPortfolioAllocation> allVersions, LocalDate date) {
    var activeAllocations =
        allVersions.stream().filter(a -> !a.getEffectiveDate().isAfter(date)).toList();
    var distinctDates =
        activeAllocations.stream()
            .map(ModelPortfolioAllocation::getEffectiveDate)
            .distinct()
            .sorted()
            .toList();
    if (distinctDates.size() < 2) {
      return Map.of();
    }
    var previousDate = distinctDates.get(distinctDates.size() - 2);
    return activeAllocations.stream()
        .filter(a -> a.getEffectiveDate().equals(previousDate))
        .collect(
            Collectors.toMap(
                ModelPortfolioAllocation::getIsin, ModelPortfolioAllocation::getWeight));
  }

  private Set<String> findTransitionIsins(
      Map<String, BigDecimal> currentWeights,
      Map<String, BigDecimal> previousWeights,
      Set<String> positionIsins,
      TulevaFund fund) {

    if (previousWeights.isEmpty()) {
      return Set.of();
    }

    var addedIsins = new HashSet<>(currentWeights.keySet());
    addedIsins.removeAll(previousWeights.keySet());
    var removedIsins = new HashSet<>(previousWeights.keySet());
    removedIsins.removeAll(currentWeights.keySet());

    if (addedIsins.isEmpty() && removedIsins.isEmpty()) {
      return Set.of();
    }

    var knownIsins = new HashSet<>(currentWeights.keySet());
    knownIsins.addAll(previousWeights.keySet());
    var unexpectedIsins = new HashSet<>(positionIsins);
    unexpectedIsins.removeAll(knownIsins);

    if (!unexpectedIsins.isEmpty()) {
      log.warn(
          "Unexpected ISINs in portfolio, skipping transition blending: fund={}, unexpected={}",
          fund,
          unexpectedIsins);
      return Set.of();
    }

    var removedAndHeld = new HashSet<>(removedIsins);
    removedAndHeld.retainAll(positionIsins);

    if (removedAndHeld.isEmpty()) {
      return Set.of();
    }

    var transitionIsins = new HashSet<>(addedIsins);
    transitionIsins.addAll(removedIsins);
    return transitionIsins;
  }

  private PeriodicTdAttribution toEntity(TdAttributionResult result) {
    var entity =
        PeriodicTdAttribution.builder()
            .fund(result.fund())
            .periodStart(result.periodStart())
            .periodEnd(result.periodEnd())
            .periodType(result.periodType())
            .fundReturn(result.fundReturn())
            .modelReturn(result.modelReturn())
            .tdGeometric(result.tdGeometric())
            .scalingFactor(result.scalingFactor())
            .mgmtFeeDrag(result.mgmtFeeDrag())
            .depotFeeDrag(result.depotFeeDrag())
            .cashDrag(result.cashDrag())
            .nonSecurityDrag(result.nonSecurityDrag())
            .weightDeviation(result.weightDeviation())
            .transactionCosts(result.transactionCosts())
            .residual(result.residual())
            .etfOcfDrag(result.etfOcfDrag())
            .etfTrackingResidual(result.etfTrackingResidual())
            .tdVsBenchmark(result.tdVsBenchmark())
            .businessDays(result.navEventCount())
            .avgAum(result.avgAum())
            .avgCashPct(result.avgCashPct())
            .checks(result.checks())
            .build();

    for (var detail : result.instrumentDetails()) {
      entity.addDetail(
          TdAttributionDetail.builder()
              .isin(detail.isin())
              .instrumentName(detail.instrumentName())
              .modelWeight(detail.modelWeight())
              .avgActualWeight(detail.avgActualWeight())
              .weightDevContribution(detail.weightDevContribution())
              .securityReturn(detail.securityReturn())
              .build());
    }

    return entity;
  }

  static BigDecimal toBigDecimal(Object value) {
    if (value instanceof BigDecimal bd) return bd;
    if (value instanceof Number n) return new BigDecimal(n.toString());
    if (value instanceof String s) return new BigDecimal(s);
    return ZERO;
  }
}
