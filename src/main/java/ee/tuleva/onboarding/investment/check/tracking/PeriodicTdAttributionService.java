package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.check.tracking.TdAttributionCalculator.DailyRecord;
import ee.tuleva.onboarding.investment.check.tracking.TdAttributionCalculator.SecurityDailyData;
import ee.tuleva.onboarding.investment.check.tracking.TdAttributionCalculator.TdAttributionInput;
import ee.tuleva.onboarding.investment.fees.FeeAccrual;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PeriodicTdAttributionService {

  private static final int SCALE = TdAttributionCalculator.SCALE;

  private final TrackingDifferenceEventRepository tdEventRepository;
  private final FeeAccrualRepository feeAccrualRepository;
  private final FeeRateRepository feeRateRepository;
  private final FundPositionRepository fundPositionRepository;
  private final FundNavQueryService fundNavQueryService;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final PeriodicTdAttributionRepository attributionRepository;
  private final TransactionExecutionRepository transactionExecutionRepository;

  private final TdAttributionCalculator calculator = new TdAttributionCalculator();

  @Transactional
  public TdAttributionResult computeAttribution(
      TulevaFund fund, LocalDate periodStart, LocalDate periodEnd, PeriodType periodType) {

    attributionRepository.deleteByFundAndPeriodStartAndPeriodEndAndPeriodType(
        fund, periodStart, periodEnd, periodType);

    var input = buildInput(fund, periodStart, periodEnd, periodType);
    var result = calculator.calculate(input);

    var entity = toEntity(result);
    attributionRepository.save(entity);

    log.info(
        "TD attribution computed: fund={}, period={}-{}, td={}bps, residual={}bps",
        fund,
        periodStart,
        periodEnd,
        result.tdGeometric().multiply(BigDecimal.valueOf(10000)).setScale(1, HALF_UP),
        result.residual().multiply(BigDecimal.valueOf(10000)).setScale(1, HALF_UP));

    return result;
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

    var dailyRecords = buildDailyRecords(fund, tdEvents, modelAllocations);

    var mgmtFeeDragTotal = computeFeeDragPeriod(feeAccruals, FeeType.MANAGEMENT);
    var depotFeeDragTotal = computeFeeDragPeriod(feeAccruals, FeeType.DEPOT);

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
            fund.getCode(), periodStart, periodEnd);
    var txnCostReturn =
        avgAum.signum() > 0 ? txnCosts.negate().divide(avgAum, SCALE, HALF_UP) : ZERO;

    var bmModelEvents =
        tdEventRepository.findDeduplicatedEventsForPeriod(
            fund, BENCHMARK_MODEL, periodStart, periodEnd);
    var etfTrackingArithmetic =
        bmModelEvents.stream()
            .map(TrackingDifferenceEvent::getTrackingDifference)
            .reduce(ZERO, BigDecimal::add);

    var weightedOcf = computeWeightedOcf();
    var etfOcfDrag =
        weightedOcf
            .negate()
            .multiply(BigDecimal.valueOf(calendarDays))
            .divide(BigDecimal.valueOf(365), SCALE, HALF_UP);

    return TdAttributionInput.builder()
        .fund(fund)
        .periodStart(periodStart)
        .periodEnd(periodEnd)
        .periodType(periodType)
        .calendarDays(calendarDays)
        .mgmtFeeDragPeriod(mgmtFeeDragReturn)
        .depotFeeDragPeriod(depotFeeDragReturn)
        .transactionCostsPeriod(txnCostReturn)
        .etfOcfDragPeriod(etfOcfDrag)
        .etfTrackingResidualArithmetic(etfTrackingArithmetic)
        .expectedAnnualFeeRate(expectedAnnualFeeRate)
        .dailyRecords(dailyRecords)
        .build();
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

  private BigDecimal computeWeightedOcf() {
    return ZERO;
  }

  private BigDecimal computeFeeDragPeriod(List<FeeAccrual> accruals, FeeType feeType) {
    return accruals.stream()
        .filter(a -> a.feeType() == feeType)
        .map(a -> feeType == FeeType.DEPOT ? a.dailyAmountGross() : a.dailyAmountNet())
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
    var feeAccrualLiabilities = fundNavQueryService.findFeeAccrualLiabilities(fund.getCode(), date);

    // Non-security value: AUM - securities - cash - feeAccruals
    // LIABILITY_FEE market_values are NEGATIVE in nav_report (NavReportMapper.liabilityFeeRow
    // stores amount.negate()), so subtracting a negative adds them back — correctly excluding
    // fee accruals from this component (they are attributed in components A+B).
    var nonSecurityValue = aum.subtract(securities).subtract(cash).subtract(feeAccrualLiabilities);

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
            .businessDays(result.businessDays())
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
