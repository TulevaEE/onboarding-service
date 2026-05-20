package ee.tuleva.onboarding.investment.check.tracking;

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
import ee.tuleva.onboarding.savings.fund.nav.FundNavQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
class PeriodicTdAttributionService {

  private static final int SCALE = 10;

  private final TrackingDifferenceEventRepository tdEventRepository;
  private final FeeAccrualRepository feeAccrualRepository;
  private final FeeRateRepository feeRateRepository;
  private final FundPositionRepository fundPositionRepository;
  private final FundNavQueryService fundNavQueryService;
  private final ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  private final PeriodicTdAttributionRepository attributionRepository;

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

  private TdAttributionInput buildInput(
      TulevaFund fund, LocalDate periodStart, LocalDate periodEnd, PeriodType periodType) {

    var tdEvents =
        tdEventRepository.findDeduplicatedEventsForPeriod(
            fund, MODEL_PORTFOLIO, periodStart, periodEnd);

    var feeAccruals = feeAccrualRepository.findByFundAndDateRange(fund, periodStart, periodEnd);

    var modelAllocations =
        modelPortfolioAllocationRepository.findVersionsActiveDuringPeriod(
            fund, periodStart, periodEnd);

    var mgmtFeeDrag = computeFeeDragPeriod(feeAccruals, FeeType.MANAGEMENT);
    var depotFeeDrag = computeFeeDragPeriod(feeAccruals, FeeType.DEPOT);

    var avgAumForFees = computeAvgAum(fund, tdEvents);
    var mgmtFeeDragReturn =
        avgAumForFees.signum() > 0
            ? mgmtFeeDrag.negate().divide(avgAumForFees, SCALE, HALF_UP)
            : ZERO;
    var depotFeeDragReturn =
        avgAumForFees.signum() > 0
            ? depotFeeDrag.negate().divide(avgAumForFees, SCALE, HALF_UP)
            : ZERO;

    var calendarDays = (int) ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
    var expectedAnnualFeeRate =
        feeRateRepository
            .findValidRate(fund, FeeType.MANAGEMENT, periodEnd)
            .map(r -> r.annualRate())
            .orElse(ZERO);

    var dailyRecords = buildDailyRecords(fund, tdEvents, modelAllocations);

    return TdAttributionInput.builder()
        .fund(fund)
        .periodStart(periodStart)
        .periodEnd(periodEnd)
        .periodType(periodType)
        .calendarDays(calendarDays)
        .mgmtFeeDragPeriod(mgmtFeeDragReturn)
        .depotFeeDragPeriod(depotFeeDragReturn)
        .expectedAnnualFeeRate(expectedAnnualFeeRate)
        .dailyRecords(dailyRecords)
        .build();
  }

  private BigDecimal computeFeeDragPeriod(List<FeeAccrual> accruals, FeeType feeType) {
    return accruals.stream()
        .filter(a -> a.feeType() == feeType)
        .map(a -> feeType == FeeType.DEPOT ? a.dailyAmountGross() : a.dailyAmountNet())
        .reduce(ZERO, BigDecimal::add);
  }

  private BigDecimal computeAvgAum(TulevaFund fund, List<TrackingDifferenceEvent> tdEvents) {
    var totalAum = ZERO;
    int count = 0;
    for (var event : tdEvents) {
      var aum = fundNavQueryService.findAum(fund.getCode(), event.getCheckDate());
      if (aum != null && aum.signum() > 0) {
        totalAum = totalAum.add(aum);
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
      var fundCode = fund.getCode();

      var aum = fundNavQueryService.findAum(fundCode, date);
      if (aum == null || aum.signum() <= 0) {
        log.warn("Missing AUM for daily record: fund={}, date={}", fund, date);
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

      var cash = fundNavQueryService.findCashValue(fundCode, date);
      var securities = fundNavQueryService.findSecuritiesTotalValue(fundCode, date);
      var feeAccrualLiabilities = fundNavQueryService.findFeeAccrualLiabilities(fundCode, date);

      // Non-security value: AUM - securities - cash - feeAccruals
      // LIABILITY_FEE market_values are NEGATIVE in nav_report (NavReportMapper.liabilityFeeRow
      // stores amount.negate()), so subtracting a negative adds them back — correctly excluding
      // fee accruals from this component (they are attributed in components A+B).
      var nonSecurityValue =
          aum.subtract(securities).subtract(cash).subtract(feeAccrualLiabilities);

      var securityDailyData = buildSecurityDailyData(fund, event, modelAllocations, date);

      records.add(
          DailyRecord.builder()
              .date(date)
              .fundReturn(event.getFundReturn())
              .modelReturn(event.getBenchmarkReturn())
              .aum(aum)
              .cashValue(cash)
              .nonSecurityValue(nonSecurityValue)
              .securities(securityDailyData)
              .build());
    }

    return records;
  }

  @SuppressWarnings("unchecked")
  private List<SecurityDailyData> buildSecurityDailyData(
      TulevaFund fund,
      TrackingDifferenceEvent event,
      List<ModelPortfolioAllocation> modelAllocations,
      LocalDate date) {

    var result = event.getResult();
    var attributions =
        (List<Map<String, Object>>) result.getOrDefault("securityAttributions", List.of());
    if (attributions.isEmpty()) {
      return List.of();
    }

    // Get actual positions for normalization
    var positions = fundPositionRepository.findByNavDateAndFundAndAccountType(date, fund, SECURITY);
    var totalSecurityValue =
        positions.stream().map(FundPosition::getMarketValue).reduce(ZERO, BigDecimal::add);

    if (totalSecurityValue.signum() <= 0) {
      return List.of();
    }

    var positionByIsin =
        positions.stream()
            .filter(p -> p.getAccountId() != null)
            .collect(Collectors.toMap(FundPosition::getAccountId, p -> p, (a, b) -> a));

    // Get model weights for this date
    var modelWeights = getModelWeightsForDate(modelAllocations, date);

    var result2 = new ArrayList<SecurityDailyData>();

    for (var attr : attributions) {
      var isin = (String) attr.get("isin");
      var securityReturn = toBigDecimal(attr.get("securityReturn"));

      // Normalize actual weight against total security value
      var position = positionByIsin.get(isin);
      var actualMv = position != null ? position.getMarketValue() : ZERO;
      var normalizedActualWeight = actualMv.divide(totalSecurityValue, SCALE, HALF_UP);

      var modelWeight = modelWeights.getOrDefault(isin, ZERO);
      var normalizedWeightDiff = normalizedActualWeight.subtract(modelWeight);

      result2.add(
          SecurityDailyData.builder()
              .isin(isin)
              .modelWeight(modelWeight)
              .actualWeight(normalizedActualWeight)
              .normalizedWeightDiff(normalizedWeightDiff)
              .securityReturn(securityReturn)
              .build());
    }

    return result2;
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

  private static BigDecimal toBigDecimal(Object value) {
    if (value instanceof BigDecimal bd) return bd;
    if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
    if (value instanceof String s) return new BigDecimal(s);
    return ZERO;
  }
}
