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
import org.jspecify.annotations.Nullable;

@Slf4j
class TdAttributionCalculator {

  static final int SCALE = 10;
  private static final BigDecimal CARINO_NEAR_EQUAL = new BigDecimal("0.0000000001");
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

    var periodCoefficient = carinoCoefficient(fundCumulative, modelCumulative);

    var cashDragNumerator = ZERO;
    var nonSecDragNumerator = ZERO;
    var weightDevNumerator = ZERO;
    var coefficientSum = ZERO;

    var totalAum = ZERO;
    var totalCashPct = ZERO;
    int aumDays = 0;

    var instrumentContributions = new LinkedHashMap<String, InstrumentAccumulator>();

    for (var day : dailyRecords) {
      var aum = day.aum();
      if (aum.signum() <= 0) {
        log.warn("Skipping zero-AUM day: fund={}, date={}", input.fund(), day.date());
        continue;
      }

      totalAum = totalAum.add(aum);
      aumDays++;

      var dailyCoefficient = carinoCoefficient(day.fundReturn(), day.modelReturn());
      coefficientSum = coefficientSum.add(dailyCoefficient);

      var cashPct = day.cashValue().divide(aum, SCALE, HALF_UP);
      totalCashPct = totalCashPct.add(cashPct);
      var cashEffect = cashPct.negate().multiply(day.modelReturn());
      cashDragNumerator = cashDragNumerator.add(dailyCoefficient.multiply(cashEffect));

      var nonSecPct = day.nonSecurityValue().divide(aum, SCALE, HALF_UP);
      var nonSecEffect = nonSecPct.negate().multiply(day.modelReturn());
      nonSecDragNumerator = nonSecDragNumerator.add(dailyCoefficient.multiply(nonSecEffect));

      for (var sec : day.securities()) {
        var contrib = sec.normalizedWeightDiff().multiply(sec.securityReturn());
        weightDevNumerator = weightDevNumerator.add(dailyCoefficient.multiply(contrib));

        instrumentContributions
            .computeIfAbsent(sec.isin(), k -> new InstrumentAccumulator(sec.isin()))
            .add(sec, contrib, dailyCoefficient);
      }
    }

    var cashDrag = linkDaily(cashDragNumerator, periodCoefficient);
    var nonSecurityDrag = linkDaily(nonSecDragNumerator, periodCoefficient);
    var weightDeviation = linkDaily(weightDevNumerator, periodCoefficient);

    var periodLink = periodLinkMultiplier(coefficientSum, periodCoefficient, aumDays, input);
    var mgmtFeeDrag = orZero(input.mgmtFeeDragPeriod()).multiply(periodLink).setScale(8, HALF_UP);
    var depotFeeDrag = orZero(input.depotFeeDragPeriod()).multiply(periodLink).setScale(8, HALF_UP);
    var transactionCosts =
        orZero(input.transactionCostsPeriod()).multiply(periodLink).setScale(8, HALF_UP);

    var tdGeometricRounded = tdGeometric.setScale(8, HALF_UP);
    var explained =
        mgmtFeeDrag
            .add(depotFeeDrag)
            .add(cashDrag)
            .add(nonSecurityDrag)
            .add(weightDeviation)
            .add(transactionCosts);
    var residual = tdGeometricRounded.subtract(explained);

    var avgAum = aumDays > 0 ? totalAum.divide(BigDecimal.valueOf(aumDays), 2, HALF_UP) : ZERO;
    var avgCashPct =
        aumDays > 0 ? totalCashPct.divide(BigDecimal.valueOf(aumDays), SCALE, HALF_UP) : ZERO;

    int businessDays = dailyRecords.size();

    var checks =
        buildChecks(tdGeometricRounded, explained.add(residual), residual, periodLink, input);

    var instrumentDetails =
        instrumentContributions.values().stream()
            .map(acc -> acc.toAttribution(periodCoefficient))
            .toList();

    // View 2: model-vs-benchmark layer, linked with the same bounded period multiplier
    var etfOcfDrag = orZero(input.etfOcfDragPeriod()).multiply(periodLink).setScale(8, HALF_UP);
    var etfTrackingResidual =
        orZero(input.etfTrackingResidualArithmetic()).multiply(periodLink).setScale(8, HALF_UP);
    var tdVsBenchmark =
        tdGeometricRounded.add(etfOcfDrag).add(etfTrackingResidual).setScale(8, HALF_UP);

    return TdAttributionResult.builder()
        .fund(input.fund())
        .periodStart(input.periodStart())
        .periodEnd(input.periodEnd())
        .periodType(input.periodType())
        .fundReturn(fundCumulative.setScale(8, HALF_UP))
        .modelReturn(modelCumulative.setScale(8, HALF_UP))
        .tdGeometric(tdGeometricRounded)
        .scalingFactor(periodLink.setScale(8, HALF_UP))
        .mgmtFeeDrag(mgmtFeeDrag)
        .depotFeeDrag(depotFeeDrag)
        .cashDrag(cashDrag)
        .nonSecurityDrag(nonSecurityDrag)
        .weightDeviation(weightDeviation)
        .transactionCosts(transactionCosts)
        .residual(residual)
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

  // Cariño (1999) logarithmic linking coefficient: maps a single-period arithmetic excess
  // return onto the geometric layer. Finite as benchmarkReturn -> portfolioReturn (limit
  // 1/(1+portfolioReturn)), so it never blows up the way tdGeometric/tdArithmetic did.
  private BigDecimal carinoCoefficient(BigDecimal portfolioReturn, BigDecimal benchmarkReturn) {
    var diff = portfolioReturn.subtract(benchmarkReturn);
    double r = portfolioReturn.doubleValue();
    if (diff.abs().compareTo(CARINO_NEAR_EQUAL) < 0) {
      return BigDecimal.valueOf(1.0 / (1.0 + r));
    }
    double coefficient =
        (Math.log1p(r) - Math.log1p(benchmarkReturn.doubleValue())) / diff.doubleValue();
    return BigDecimal.valueOf(coefficient);
  }

  // Daily-attributed effect linked to the geometric layer: sum_t(k_t * effect_t) / k.
  private BigDecimal linkDaily(BigDecimal numerator, BigDecimal periodCoefficient) {
    if (periodCoefficient.signum() == 0) {
      return numerator.setScale(8, HALF_UP);
    }
    return numerator.divide(periodCoefficient, SCALE, HALF_UP).setScale(8, HALF_UP);
  }

  // Period-level effects (fees, transaction costs, ETF OCF) have no daily profile here, so we
  // spread them uniformly across linked days and link them: (sum_t k_t / N) / k. This stays
  // close to 1 and never diverges, replacing the old tdGeometric/tdArithmetic scale factor.
  private BigDecimal periodLinkMultiplier(
      BigDecimal coefficientSum,
      BigDecimal periodCoefficient,
      int linkedDays,
      TdAttributionInput input) {
    if (linkedDays == 0 || periodCoefficient.signum() == 0) {
      return ONE;
    }
    var multiplier =
        coefficientSum.divide(
            periodCoefficient.multiply(BigDecimal.valueOf(linkedDays)), SCALE, HALF_UP);
    if (multiplier.abs().compareTo(EXTREME_SCALE) > 0) {
      log.warn(
          "Extreme period link multiplier: fund={}, period={}-{}, multiplier={}",
          input.fund(),
          input.periodStart(),
          input.periodEnd(),
          multiplier);
    }
    return multiplier;
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
      BigDecimal linkedComponentSum,
      BigDecimal residual,
      BigDecimal periodLink,
      TdAttributionInput input) {
    var sumCheck = tdGeometric.subtract(linkedComponentSum).abs();
    var residualBps = residual.multiply(BigDecimal.valueOf(10000));

    var feeXcheck = ZERO;
    if (input.expectedAnnualFeeRate() != null && input.expectedAnnualFeeRate().signum() > 0) {
      var expectedFeeDrag =
          input
              .expectedAnnualFeeRate()
              .negate()
              .multiply(BigDecimal.valueOf(input.calendarDays()))
              .divide(BigDecimal.valueOf(365), SCALE, HALF_UP);
      feeXcheck = orZero(input.mgmtFeeDragPeriod()).subtract(expectedFeeDrag).abs();
    }

    return Map.of(
        "sumCheck", sumCheck.setScale(8, HALF_UP),
        "feeXcheck", feeXcheck.setScale(8, HALF_UP),
        "scalingFactor", periodLink.setScale(8, HALF_UP),
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
      @Nullable String instrumentName,
      BigDecimal modelWeight,
      BigDecimal actualWeight,
      BigDecimal normalizedWeightDiff,
      BigDecimal securityReturn) {}

  private static class InstrumentAccumulator {

    final String isin;
    @Nullable String instrumentName;
    BigDecimal totalModelWeight = ZERO;
    BigDecimal totalActualWeight = ZERO;
    BigDecimal contributionNumerator = ZERO;
    BigDecimal compoundReturn = ONE;
    int days = 0;

    InstrumentAccumulator(String isin) {
      this.isin = isin;
    }

    void add(SecurityDailyData sec, BigDecimal contribution, BigDecimal dailyCoefficient) {
      if (instrumentName == null && sec.instrumentName() != null) {
        instrumentName = sec.instrumentName();
      }
      totalModelWeight = totalModelWeight.add(sec.modelWeight());
      totalActualWeight = totalActualWeight.add(sec.actualWeight());
      contributionNumerator = contributionNumerator.add(dailyCoefficient.multiply(contribution));
      compoundReturn = compoundReturn.multiply(ONE.add(sec.securityReturn()));
      days++;
    }

    TdAttributionResult.InstrumentAttribution toAttribution(BigDecimal periodCoefficient) {
      var avgModel =
          days > 0 ? totalModelWeight.divide(BigDecimal.valueOf(days), 6, HALF_UP) : ZERO;
      var avgActual =
          days > 0 ? totalActualWeight.divide(BigDecimal.valueOf(days), 6, HALF_UP) : ZERO;
      var linkedContribution =
          periodCoefficient.signum() == 0
              ? contributionNumerator.setScale(8, HALF_UP)
              : contributionNumerator
                  .divide(periodCoefficient, SCALE, HALF_UP)
                  .setScale(8, HALF_UP);
      return TdAttributionResult.InstrumentAttribution.builder()
          .isin(isin)
          .instrumentName(instrumentName != null ? instrumentName : isin)
          .modelWeight(avgModel)
          .avgActualWeight(avgActual)
          .weightDevContribution(linkedContribution)
          .securityReturn(compoundReturn.subtract(ONE).setScale(8, HALF_UP))
          .build();
    }
  }
}
