package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.TrackingCheckType.MODEL_PORTFOLIO;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.check.tracking.TdAttributionCalculator.DailyRecord;
import ee.tuleva.onboarding.investment.check.tracking.TdAttributionCalculator.TdAttributionInput;
import ee.tuleva.onboarding.investment.config.InvestmentParameter;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.fees.FeeAccrual;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.fees.FeeRateRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.fees.InstrumentFee;
import ee.tuleva.onboarding.investment.fees.InstrumentFeeRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
  private final TdAttributionInputAssembler inputAssembler;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final PeriodicTdAttributionRepository attributionRepository;
  private final TransactionExecutionRepository transactionExecutionRepository;
  private final InstrumentFeeRepository instrumentFeeRepository;
  private final PlatformTransactionManager transactionManager;
  private final PublicHolidays publicHolidays;
  private final BenchmarkLegResolver benchmarkLegResolver;
  private final InvestmentParameterRepository parameterRepository;
  private final TrackingDifferenceNotifier notifier;

  private final TdAttributionCalculator calculator = new TdAttributionCalculator();

  // Absent or unusable, the tolerance is simply not applied: an unseeded parameter must not turn
  // every period into a false alarm.
  @Nullable
  private BigDecimal residualTolerance(LocalDate asOf) {
    try {
      return parameterRepository.findLatestValue(
          InvestmentParameter.TD_RESIDUAL_TOLERANCE_ANNUAL, asOf);
    } catch (Exception e) {
      log.warn(
          "No TD residual tolerance configured, not checking the residual: {}", e.getMessage());
      return null;
    }
  }

  public TdAttributionResult computeAttribution(
      TulevaFund fund, LocalDate periodStart, LocalDate periodEnd, PeriodType periodType) {

    var input = buildInput(fund, periodStart, periodEnd, periodType);
    var result = calculator.calculate(input);
    var entity = toEntity(result);

    replaceAttributionRowInSingleTransaction(fund, periodStart, periodEnd, periodType, entity);

    if (Boolean.FALSE.equals(result.checks().get("residualWithinTolerance"))) {
      notifier.notifyResidualOutsideTolerance(
          fund,
          periodStart,
          periodEnd,
          result.residual(),
          TdAttributionCalculator.scaledResidualTolerance(input));
    }

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
        TdAttributionInputAssembler.countSeriesGaps(
            tdEvents.stream().map(TrackingDifferenceEvent::getCheckDate).toList(), publicHolidays);
    if (seriesGapDays > 0) {
      log.warn(
          "Gaps in daily TD series may distort geometric compounding: fund={}, period={}-{}, gapDays={}",
          fund,
          periodStart,
          periodEnd,
          seriesGapDays);
    }

    var dailyRecords = inputAssembler.buildDailyRecords(fund, tdEvents, modelAllocations);

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
        .residualTolerance(residualTolerance(periodEnd))
        .etfLayerCoveredDays(etfLayer.coveredDays())
        .etfLayerUnbenchmarkedWeight(etfLayer.unbenchmarkedWeight())
        .etfLayerUnrestoredProxyWeight(etfLayer.unrestoredProxyWeight())
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

    var coveredDays = etfLayerCoveredDays(bmModelEvents);
    warnIfEtfLayerDoesNotTilePeriod(fund, periodStart, periodEnd, coveredDays);

    var allocations = latestAllocations(modelAllocations, periodEnd);
    var measuredIsins = measuredIsins(fund, periodStart, periodEnd, bmModelEvents);
    var rateByIsin =
        instrumentFeeRepository.findAllValidRates(periodEnd).stream()
            .collect(Collectors.toMap(InstrumentFee::isin, InstrumentFee::netOcf, (a, b) -> a));

    var accumulator =
        new EtfLayerAccumulator(benchmarkLegResolver, fund, measuredIsins, rateByIsin);
    for (var allocation : allocations) {
      accumulator.accumulate(allocation);
    }
    accumulator.logWarnings(periodEnd);

    return accumulator.toEtfLayer(measuredSum, coveredDays);
  }

  private int etfLayerCoveredDays(List<TrackingDifferenceEvent> bmModelEvents) {
    return bmModelEvents.stream()
        .map(TrackingDifferenceEvent::getCheckDate)
        .mapToInt(d -> (int) ChronoUnit.DAYS.between(publicHolidays.previousWorkingDay(d), d))
        .sum();
  }

  private void warnIfEtfLayerDoesNotTilePeriod(
      TulevaFund fund, LocalDate periodStart, LocalDate periodEnd, int coveredDays) {
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
}
