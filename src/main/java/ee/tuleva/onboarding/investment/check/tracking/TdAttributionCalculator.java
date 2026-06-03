package ee.tuleva.onboarding.investment.check.tracking;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class TdAttributionCalculator {

  static final int SCALE = 10;
  private static final BigDecimal NEAR_ZERO = new BigDecimal("0.0000001");
  private static final BigDecimal EXTREME_SCALE = new BigDecimal("2.0");

  TdAttributionResult calculate(TdAttributionInput input) {
    var dailyRecords = input.dailyRecords();
    if (dailyRecords.isEmpty()) {
      return emptyResult(input);
    }

    var fundCumulative =
        geometricReturn(dailyRecords.stream().map(DailyRecord::fundReturn).toList());
    var modelCumulative =
        geometricReturn(dailyRecords.stream().map(DailyRecord::modelReturn).toList());
    var tdGeometric = fundCumulative.subtract(modelCumulative);

    var mgmtFeeDragArithmetic = ZERO;
    var depotFeeDragArithmetic = ZERO;
    var cashDragArithmetic = ZERO;
    var nonSecDragArithmetic = ZERO;
    var weightDevArithmetic = ZERO;
    var tdArithmetic = ZERO;

    var totalAum = ZERO;
    var totalCashPct = ZERO;
    int aumDays = 0;

    var instrumentContributions = new LinkedHashMap<String, InstrumentAccumulator>();

    for (var day : dailyRecords) {
      var dailyTd = day.fundReturn().subtract(day.modelReturn());
      tdArithmetic = tdArithmetic.add(dailyTd);

      var aum = day.aum();
      if (aum.signum() <= 0) {
        log.warn("Skipping zero-AUM day: fund={}, date={}", input.fund(), day.date());
        continue;
      }

      totalAum = totalAum.add(aum);
      aumDays++;

      var cashPct = day.cashValue().divide(aum, SCALE, HALF_UP);
      totalCashPct = totalCashPct.add(cashPct);

      cashDragArithmetic = cashDragArithmetic.add(cashPct.negate().multiply(day.modelReturn()));

      var nonSecPct = day.nonSecurityValue().divide(aum, SCALE, HALF_UP);
      nonSecDragArithmetic =
          nonSecDragArithmetic.add(nonSecPct.negate().multiply(day.modelReturn()));

      for (var sec : day.securities()) {
        var contrib = sec.normalizedWeightDiff().multiply(sec.securityReturn());
        weightDevArithmetic = weightDevArithmetic.add(contrib);

        instrumentContributions
            .computeIfAbsent(sec.isin(), k -> new InstrumentAccumulator(sec.isin()))
            .add(sec, contrib);
      }
    }

    mgmtFeeDragArithmetic = input.mgmtFeeDragPeriod();
    depotFeeDragArithmetic = input.depotFeeDragPeriod();
    var txnCostsArithmetic = orZero(input.transactionCostsPeriod());

    var componentsArithmetic =
        mgmtFeeDragArithmetic
            .add(depotFeeDragArithmetic)
            .add(cashDragArithmetic)
            .add(nonSecDragArithmetic)
            .add(weightDevArithmetic)
            .add(txnCostsArithmetic);

    var residualArithmetic = tdArithmetic.subtract(componentsArithmetic);

    var allArithmetic =
        componentsArithmetic.add(residualArithmetic); // == tdArithmetic by construction

    var scale = computeScalingFactor(tdGeometric, allArithmetic, input);

    var avgAum = aumDays > 0 ? totalAum.divide(BigDecimal.valueOf(aumDays), 2, HALF_UP) : ZERO;
    var avgCashPct =
        aumDays > 0 ? totalCashPct.divide(BigDecimal.valueOf(aumDays), SCALE, HALF_UP) : ZERO;

    int businessDays = dailyRecords.size();

    var checks = buildChecks(tdGeometric, componentsArithmetic, residualArithmetic, scale, input);

    var instrumentDetails =
        instrumentContributions.values().stream().map(acc -> acc.toAttribution(scale)).toList();

    // View 2: fund-vs-benchmark layer (scaled together with View 1 in one chain)
    var etfOcfDrag = orZero(input.etfOcfDragPeriod()).multiply(scale).setScale(8, HALF_UP);
    var etfTrackingResidual =
        orZero(input.etfTrackingResidualArithmetic()).multiply(scale).setScale(8, HALF_UP);
    var tdVsBenchmark = tdGeometric.add(etfOcfDrag).add(etfTrackingResidual).setScale(8, HALF_UP);

    return TdAttributionResult.builder()
        .fund(input.fund())
        .periodStart(input.periodStart())
        .periodEnd(input.periodEnd())
        .periodType(input.periodType())
        .fundReturn(fundCumulative.setScale(8, HALF_UP))
        .modelReturn(modelCumulative.setScale(8, HALF_UP))
        .tdGeometric(tdGeometric.setScale(8, HALF_UP))
        .scalingFactor(scale.setScale(8, HALF_UP))
        .mgmtFeeDrag(mgmtFeeDragArithmetic.multiply(scale).setScale(8, HALF_UP))
        .depotFeeDrag(depotFeeDragArithmetic.multiply(scale).setScale(8, HALF_UP))
        .cashDrag(cashDragArithmetic.multiply(scale).setScale(8, HALF_UP))
        .nonSecurityDrag(nonSecDragArithmetic.multiply(scale).setScale(8, HALF_UP))
        .weightDeviation(weightDevArithmetic.multiply(scale).setScale(8, HALF_UP))
        .transactionCosts(txnCostsArithmetic.multiply(scale).setScale(8, HALF_UP))
        .residual(residualArithmetic.multiply(scale).setScale(8, HALF_UP))
        .etfOcfDrag(etfOcfDrag)
        .etfTrackingResidual(etfTrackingResidual)
        .tdVsBenchmark(tdVsBenchmark)
        .businessDays(businessDays)
        .avgAum(avgAum)
        .avgCashPct(avgCashPct.setScale(6, HALF_UP))
        .instrumentDetails(instrumentDetails)
        .checks(checks)
        .build();
  }

  private BigDecimal computeScalingFactor(
      BigDecimal tdGeometric, BigDecimal tdArithmetic, TdAttributionInput input) {
    if (tdArithmetic.abs().compareTo(NEAR_ZERO) < 0) {
      log.info(
          "Near-zero arithmetic TD, using scale=1: fund={}, period={}-{}",
          input.fund(),
          input.periodStart(),
          input.periodEnd());
      return ONE;
    }
    var scale = tdGeometric.divide(tdArithmetic, SCALE, HALF_UP);
    if (scale.abs().compareTo(EXTREME_SCALE) > 0) {
      log.warn(
          "Extreme scaling factor: fund={}, period={}-{}, scale={}",
          input.fund(),
          input.periodStart(),
          input.periodEnd(),
          scale);
    }
    return scale;
  }

  private BigDecimal geometricReturn(List<BigDecimal> dailyReturns) {
    var cumulative = ONE;
    for (var r : dailyReturns) {
      cumulative = cumulative.multiply(ONE.add(r));
    }
    return cumulative.subtract(ONE).setScale(SCALE, HALF_UP);
  }

  private Map<String, Object> buildChecks(
      BigDecimal tdGeometric,
      BigDecimal componentsArithmetic,
      BigDecimal residualArithmetic,
      BigDecimal scale,
      TdAttributionInput input) {
    var scaledComponents =
        componentsArithmetic.add(residualArithmetic).multiply(scale).setScale(8, HALF_UP);
    var sumCheck = tdGeometric.subtract(scaledComponents).abs();
    var residualBps = residualArithmetic.multiply(scale).multiply(BigDecimal.valueOf(10000));

    var feeXcheck = ZERO;
    if (input.expectedAnnualFeeRate() != null && input.expectedAnnualFeeRate().signum() > 0) {
      var expectedFeeDrag =
          input
              .expectedAnnualFeeRate()
              .negate()
              .multiply(BigDecimal.valueOf(input.calendarDays()))
              .divide(BigDecimal.valueOf(365), SCALE, HALF_UP);
      feeXcheck = input.mgmtFeeDragPeriod().subtract(expectedFeeDrag).abs();
    }

    return Map.of(
        "sumCheck", sumCheck.setScale(8, HALF_UP),
        "feeXcheck", feeXcheck.setScale(8, HALF_UP),
        "scalingFactor", scale.setScale(8, HALF_UP),
        "residualBps", residualBps.setScale(2, HALF_UP));
  }

  private TdAttributionResult emptyResult(TdAttributionInput input) {
    return TdAttributionResult.builder()
        .fund(input.fund())
        .periodStart(input.periodStart())
        .periodEnd(input.periodEnd())
        .periodType(input.periodType())
        .fundReturn(ZERO)
        .modelReturn(ZERO)
        .tdGeometric(ZERO)
        .scalingFactor(ONE)
        .mgmtFeeDrag(ZERO)
        .depotFeeDrag(ZERO)
        .cashDrag(ZERO)
        .nonSecurityDrag(ZERO)
        .weightDeviation(ZERO)
        .transactionCosts(ZERO)
        .residual(ZERO)
        .etfOcfDrag(ZERO)
        .etfTrackingResidual(ZERO)
        .tdVsBenchmark(ZERO)
        .businessDays(0)
        .avgAum(ZERO)
        .avgCashPct(ZERO)
        .instrumentDetails(List.of())
        .checks(Map.of())
        .build();
  }

  private static BigDecimal orZero(BigDecimal value) {
    return value != null ? value : ZERO;
  }

  @Builder
  record TdAttributionInput(
      TulevaFund fund,
      LocalDate periodStart,
      LocalDate periodEnd,
      PeriodType periodType,
      int calendarDays,
      BigDecimal mgmtFeeDragPeriod,
      BigDecimal depotFeeDragPeriod,
      BigDecimal transactionCostsPeriod,
      BigDecimal etfOcfDragPeriod,
      BigDecimal etfTrackingResidualArithmetic,
      BigDecimal expectedAnnualFeeRate,
      List<DailyRecord> dailyRecords) {}

  @Builder
  record DailyRecord(
      LocalDate date,
      BigDecimal fundReturn,
      BigDecimal modelReturn,
      BigDecimal aum,
      BigDecimal cashValue,
      BigDecimal nonSecurityValue,
      List<SecurityDailyData> securities) {}

  @Builder
  record SecurityDailyData(
      String isin,
      String instrumentName,
      BigDecimal modelWeight,
      BigDecimal actualWeight,
      BigDecimal normalizedWeightDiff,
      BigDecimal securityReturn) {}

  private static class InstrumentAccumulator {

    final String isin;
    String instrumentName;
    BigDecimal totalModelWeight = ZERO;
    BigDecimal totalActualWeight = ZERO;
    BigDecimal totalContribution = ZERO;
    BigDecimal compoundReturn = ONE;
    int days = 0;

    InstrumentAccumulator(String isin) {
      this.isin = isin;
    }

    void add(SecurityDailyData sec, BigDecimal contribution) {
      if (instrumentName == null && sec.instrumentName() != null) {
        instrumentName = sec.instrumentName();
      }
      totalModelWeight = totalModelWeight.add(sec.modelWeight());
      totalActualWeight = totalActualWeight.add(sec.actualWeight());
      totalContribution = totalContribution.add(contribution);
      compoundReturn = compoundReturn.multiply(ONE.add(sec.securityReturn()));
      days++;
    }

    TdAttributionResult.InstrumentAttribution toAttribution(BigDecimal scale) {
      var avgModel =
          days > 0 ? totalModelWeight.divide(BigDecimal.valueOf(days), 6, HALF_UP) : ZERO;
      var avgActual =
          days > 0 ? totalActualWeight.divide(BigDecimal.valueOf(days), 6, HALF_UP) : ZERO;
      return TdAttributionResult.InstrumentAttribution.builder()
          .isin(isin)
          .instrumentName(instrumentName != null ? instrumentName : isin)
          .modelWeight(avgModel)
          .avgActualWeight(avgActual)
          .weightDevContribution(totalContribution.multiply(scale).setScale(8, HALF_UP))
          .securityReturn(compoundReturn.subtract(ONE).setScale(8, HALF_UP))
          .build();
    }
  }
}
