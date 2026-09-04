package ee.tuleva.onboarding.investment.check.tracking;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.math.MathContext;
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
  private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");
  private static final MathContext TOLERANCE_MATH = new MathContext(16, HALF_UP);

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

    int navEventCount = dailyRecords.size();
    var attributedDays = aumDays;

    var checks =
        buildChecks(
            tdGeometricRounded,
            explained.add(residual),
            residual,
            periodLink,
            input,
            attributedDays,
            navEventCount);

    var instrumentDetails =
        instrumentContributions.values().stream()
            .map(acc -> acc.toAttribution(periodCoefficient, attributedDays))
            .toList();

    var etfLayer = computeEtfLayer(input, tdGeometricRounded);

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
        .etfOcfDrag(etfLayer.ocfDrag())
        .etfTrackingResidual(etfLayer.trackingResidual())
        .tdVsBenchmark(etfLayer.tdVsBenchmark())
        .navEventCount(navEventCount)
        .avgAum(avgAum)
        .avgCashPct(avgCashPct.setScale(6, HALF_UP))
        .instrumentDetails(instrumentDetails)
        .checks(checks)
        .build();
  }

  private record EtfLayer(
      BigDecimal ocfDrag, BigDecimal trackingResidual, BigDecimal tdVsBenchmark) {}

  private EtfLayer computeEtfLayer(TdAttributionInput input, BigDecimal tdGeometric) {
    if (input.benchmarkModelSumPeriod() == null) {
      return new EtfLayer(ZERO, ZERO, tdGeometric);
    }
    var ocfDrag = orZero(input.etfOcfDragPeriod()).setScale(8, HALF_UP);
    var modelVsIndex =
        input
            .benchmarkModelSumPeriod()
            .add(orZero(input.benchmarkProxyOcfDragPeriod()))
            .setScale(8, HALF_UP);
    var trackingResidual = modelVsIndex.subtract(ocfDrag).setScale(8, HALF_UP);
    return new EtfLayer(
        ocfDrag, trackingResidual, tdGeometric.add(modelVsIndex).setScale(8, HALF_UP));
  }

  private BigDecimal carinoCoefficient(BigDecimal portfolioReturn, BigDecimal benchmarkReturn) {
    if (isOutsideLogDomain(portfolioReturn) || isOutsideLogDomain(benchmarkReturn)) {
      log.warn(
          "Cariño coefficient input <= -100%, using 1.0: portfolioReturn={}, benchmarkReturn={}",
          portfolioReturn, benchmarkReturn);
      return ONE;
    }
    var diff = portfolioReturn.subtract(benchmarkReturn);
    double r = portfolioReturn.doubleValue();
    if (diff.abs().compareTo(CARINO_NEAR_EQUAL) < 0) {
      return BigDecimal.valueOf(1.0 / (1.0 + r));
    }
    double coefficient =
        (Math.log1p(r) - Math.log1p(benchmarkReturn.doubleValue())) / diff.doubleValue();
    return BigDecimal.valueOf(coefficient);
  }

  private boolean isOutsideLogDomain(BigDecimal dailyReturn) {
    return ONE.add(dailyReturn).signum() <= 0;
  }

  private BigDecimal linkDaily(BigDecimal numerator, BigDecimal periodCoefficient) {
    if (periodCoefficient.signum() == 0) {
      return numerator.setScale(8, HALF_UP);
    }
    return numerator.divide(periodCoefficient, SCALE, HALF_UP).setScale(8, HALF_UP);
  }

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

  // The tolerance is stored as an annual rate and scaled by the square root of the period's share
  // of a year, because it is a band on noise: independent daily errors accumulate with the square
  // root of time, not linearly. A residual that instead grows linearly with the period length is a
  // systematic leak - a component the attribution is missing - and widening the band would hide it
  // rather than measure it. Comparing an observed quarterly residual against sqrt(3) times the
  // monthly one is what tells the two apart.
  @Nullable
  static BigDecimal scaledResidualTolerance(TdAttributionInput input) {
    var annual = input.residualTolerance();
    if (annual == null || annual.signum() <= 0 || input.calendarDays() <= 0) {
      return null;
    }
    var yearFraction =
        BigDecimal.valueOf(input.calendarDays()).divide(DAYS_IN_YEAR, TOLERANCE_MATH);
    return annual.multiply(yearFraction.sqrt(TOLERANCE_MATH)).setScale(SCALE, HALF_UP);
  }

  private Map<String, Object> buildChecks(
      BigDecimal tdGeometric,
      BigDecimal linkedComponentSum,
      BigDecimal residual,
      BigDecimal periodLink,
      TdAttributionInput input,
      int attributedDays,
      int navEventCount) {
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

    var checks = new LinkedHashMap<String, Object>();
    checks.put("attributedDays", attributedDays);
    checks.put("unattributedDays", navEventCount - attributedDays);
    checks.put("sumCheck", sumCheck.setScale(8, HALF_UP));
    checks.put("feeXcheck", feeXcheck.setScale(8, HALF_UP));
    checks.put("scalingFactor", periodLink.setScale(8, HALF_UP));
    checks.put("residualBps", residualBps.setScale(2, HALF_UP));
    // sumCheck cannot fail - residual is defined as the tracking difference minus the components,
    // so they always add up. How big the residual is, is the only real signal. With no tolerance
    // configured there is no verdict to give: writing "within tolerance" would stamp a measured
    // pass on every period that predates the parameter, which is what the backfill runs first.
    var scaledTolerance = scaledResidualTolerance(input);
    if (scaledTolerance != null) {
      checks.put("residualWithinTolerance", residual.abs().compareTo(scaledTolerance) <= 0);
      checks.put(
          "residualToleranceBps",
          scaledTolerance.multiply(BigDecimal.valueOf(10000)).setScale(2, HALF_UP));
    }
    checks.put("seriesGapDays", input.seriesGapDays());
    checks.put("etfLayerMeasured", input.benchmarkModelSumPeriod() != null);
    checks.put("etfLayerCoveredDays", input.etfLayerCoveredDays());
    checks.put(
        "etfLayerUnbenchmarkedWeight",
        orZero(input.etfLayerUnbenchmarkedWeight()).setScale(6, HALF_UP));
    checks.put(
        "etfLayerUnrestoredProxyWeight",
        orZero(input.etfLayerUnrestoredProxyWeight()).setScale(6, HALF_UP));
    return Map.copyOf(checks);
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
        .navEventCount(0)
        .avgAum(ZERO)
        .avgCashPct(ZERO)
        .instrumentDetails(List.of())
        .checks(Map.of("etfLayerMeasured", false, "attributedDays", 0, "unattributedDays", 0))
        .build();
  }

  private static BigDecimal orZero(@Nullable BigDecimal value) {
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
      @Nullable BigDecimal benchmarkModelSumPeriod,
      @Nullable BigDecimal benchmarkProxyOcfDragPeriod,
      BigDecimal expectedAnnualFeeRate,
      int seriesGapDays,
      @Nullable BigDecimal residualTolerance,
      int etfLayerCoveredDays,
      BigDecimal etfLayerUnbenchmarkedWeight,
      BigDecimal etfLayerUnrestoredProxyWeight,
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
    }

    // A shared denominator is what lets the weights sum to one across a period containing an entry
    // or an exit - the period this detail is read for. Dividing by each instrument's own days
    // gives every row a different denominator and a half-held instrument its undiluted weight.
    TdAttributionResult.InstrumentAttribution toAttribution(
        BigDecimal periodCoefficient, int attributedDays) {
      var avgModel =
          attributedDays > 0
              ? totalModelWeight.divide(BigDecimal.valueOf(attributedDays), 6, HALF_UP)
              : ZERO;
      var avgActual =
          attributedDays > 0
              ? totalActualWeight.divide(BigDecimal.valueOf(attributedDays), 6, HALF_UP)
              : ZERO;
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
