package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.investment.check.tracking.PeriodType.MONTHLY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ee.tuleva.onboarding.investment.check.tracking.TdAttributionCalculator.DailyRecord;
import ee.tuleva.onboarding.investment.check.tracking.TdAttributionCalculator.SecurityDailyData;
import ee.tuleva.onboarding.investment.check.tracking.TdAttributionCalculator.TdAttributionInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TdAttributionCalculatorTest {

  private static final LocalDate PERIOD_START = LocalDate.of(2026, 4, 1);
  private static final LocalDate PERIOD_END = LocalDate.of(2026, 4, 30);

  private final TdAttributionCalculator calculator = new TdAttributionCalculator();

  @Test
  void geometricLinkingProducesCorrectCompoundReturn() {
    var days = buildConstantDays(20, "0.0005", "0.0007");
    var input = inputWith(days, ZERO, ZERO);

    var result = calculator.calculate(input);

    // fund: (1.0005)^20 - 1 = ~0.01004512
    // model: (1.0007)^20 - 1 = ~0.01408637
    assertThat(result.fundReturn())
        .isCloseTo(new BigDecimal("0.01004512"), within(new BigDecimal("0.00001")));
    assertThat(result.modelReturn())
        .isCloseTo(new BigDecimal("0.01408637"), within(new BigDecimal("0.00001")));
    assertThat(result.tdGeometric())
        .isCloseTo(new BigDecimal("-0.00404125"), within(new BigDecimal("0.00001")));
  }

  @Test
  void componentsSumToGeometricTdAfterScaling() {
    var days = buildDaysWithComponents(10);
    var mgmtFee = new BigDecimal("-0.00022");
    var depotFee = new BigDecimal("-0.00002");
    var input = inputWith(days, mgmtFee, depotFee);

    var result = calculator.calculate(input);

    var componentSum =
        result
            .mgmtFeeDrag()
            .add(result.depotFeeDrag())
            .add(result.cashDrag())
            .add(result.nonSecurityDrag())
            .add(result.weightDeviation())
            .add(result.transactionCosts())
            .add(result.residual());

    assertThat(componentSum).isCloseTo(result.tdGeometric(), within(new BigDecimal("0.00000001")));
  }

  @Test
  void nearZeroArithmeticTdUsesScaleOne() {
    var days =
        List.of(dailyRecord(PERIOD_START, "0.001", "0.001", "1000000", "10000", "0", List.of()));
    var input = inputWith(days, ZERO, ZERO);

    var result = calculator.calculate(input);

    assertThat(result.scalingFactor()).isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void linkingFactorStaysBoundedWhenArithmeticTdNearlyCancels() {
    // Daily TDs alternate +20bps / -20bps, so the arithmetic TD sum is ~0 while the
    // geometric TD is small but non-zero. The old single-scalar scale = tdGeo/tdArith
    // blew up here; the Cariño period link multiplier stays ~1.
    var days = new ArrayList<DailyRecord>();
    for (int i = 0; i < 20; i++) {
      var fund = i % 2 == 0 ? "0.0030" : "0.0010";
      var model = i % 2 == 0 ? "0.0010" : "0.0030";
      days.add(
          dailyRecord(PERIOD_START.plusDays(i), fund, model, "1000000", "10000", "0", List.of()));
    }
    var input = inputWith(days, new BigDecimal("-0.00022"), new BigDecimal("-0.00002"));

    var result = calculator.calculate(input);

    assertThat(result.scalingFactor()).isCloseTo(BigDecimal.ONE, within(new BigDecimal("0.05")));
    var componentSum =
        result
            .mgmtFeeDrag()
            .add(result.depotFeeDrag())
            .add(result.cashDrag())
            .add(result.nonSecurityDrag())
            .add(result.weightDeviation())
            .add(result.transactionCosts())
            .add(result.residual());
    assertThat(componentSum).isCloseTo(result.tdGeometric(), within(new BigDecimal("0.00000001")));
  }

  @Test
  void carinoLinkedWeightDeviationMatchesIndependentReference() {
    // Pins the daily-linking math itself, NOT the residual plug: weightDeviation must equal an
    // independently computed Σ(k_t · weightDiff_t · return_t) / k. Distinct daily fund/model
    // returns make k_1 ≠ k_2, so a wrong coefficient or wrong divisor would fail here even though
    // the residual would still close.
    var sec1 =
        SecurityDailyData.builder()
            .isin("IE001")
            .instrumentName("Sec")
            .modelWeight(new BigDecimal("0.50"))
            .actualWeight(new BigDecimal("0.55"))
            .normalizedWeightDiff(new BigDecimal("0.05"))
            .securityReturn(new BigDecimal("0.02"))
            .build();
    var sec2 =
        SecurityDailyData.builder()
            .isin("IE001")
            .instrumentName("Sec")
            .modelWeight(new BigDecimal("0.50"))
            .actualWeight(new BigDecimal("0.47"))
            .normalizedWeightDiff(new BigDecimal("-0.03"))
            .securityReturn(new BigDecimal("-0.01"))
            .build();
    var days =
        List.of(
            dailyRecord(PERIOD_START, "0.010", "0.012", "1000000", "0", "0", List.of(sec1)),
            dailyRecord(
                PERIOD_START.plusDays(1), "-0.005", "-0.003", "1000000", "0", "0", List.of(sec2)));
    var input = inputWith(days, ZERO, ZERO);

    var result = calculator.calculate(input);

    var k1 = referenceCarino(0.010, 0.012);
    var k2 = referenceCarino(-0.005, -0.003);
    var fundCum = 1.010 * 0.995 - 1.0;
    var modelCum = 1.012 * 0.997 - 1.0;
    var k = referenceCarino(fundCum, modelCum);
    var num = k1 * (0.05 * 0.02) + k2 * (-0.03 * -0.01);
    var expected = BigDecimal.valueOf(num / k);

    assertThat(result.weightDeviation()).isCloseTo(expected, within(new BigDecimal("0.00000001")));
  }

  @Test
  void carinoLinkingIsExactForVolatileMultiDayReturns() {
    // Two securities with day-varying weights and returns; components must still sum
    // exactly to the geometric TD after Cariño linking.
    var days = new ArrayList<DailyRecord>();
    for (int i = 0; i < 15; i++) {
      var r1 = i % 3 == 0 ? "0.02" : "-0.01";
      var r2 = i % 2 == 0 ? "-0.015" : "0.018";
      var sec1 =
          SecurityDailyData.builder()
              .isin("IE001")
              .instrumentName("Sec One")
              .modelWeight(new BigDecimal("0.50"))
              .actualWeight(new BigDecimal("0.55"))
              .normalizedWeightDiff(new BigDecimal("0.05"))
              .securityReturn(new BigDecimal(r1))
              .build();
      var sec2 =
          SecurityDailyData.builder()
              .isin("IE002")
              .instrumentName("Sec Two")
              .modelWeight(new BigDecimal("0.50"))
              .actualWeight(new BigDecimal("0.45"))
              .normalizedWeightDiff(new BigDecimal("-0.05"))
              .securityReturn(new BigDecimal(r2))
              .build();
      var fund = i % 2 == 0 ? "0.004" : "-0.002";
      var model = i % 2 == 0 ? "0.003" : "-0.001";
      days.add(
          dailyRecord(
              PERIOD_START.plusDays(i), fund, model, "1000000", "5000", "0", List.of(sec1, sec2)));
    }
    var input = inputWith(days, new BigDecimal("-0.00022"), ZERO);

    var result = calculator.calculate(input);

    var componentSum =
        result
            .mgmtFeeDrag()
            .add(result.depotFeeDrag())
            .add(result.cashDrag())
            .add(result.nonSecurityDrag())
            .add(result.weightDeviation())
            .add(result.transactionCosts())
            .add(result.residual());
    assertThat(componentSum).isCloseTo(result.tdGeometric(), within(new BigDecimal("0.00000001")));
    // Per-instrument linked contributions reconcile to the aggregate weight deviation.
    var instrumentSum =
        result.instrumentDetails().stream()
            .map(d -> d.weightDevContribution())
            .reduce(ZERO, BigDecimal::add);
    assertThat(instrumentSum)
        .isCloseTo(result.weightDeviation(), within(new BigDecimal("0.0000001")));
  }

  @Test
  void returnAtOrBelowMinusOneDoesNotThrow() {
    // ln(1+x) is undefined for x <= -1; a -100% day must not crash the calculator.
    var days =
        List.of(
            dailyRecord(PERIOD_START, "-1.0", "0.001", "1000000", "10000", "0", List.of()),
            dailyRecord(
                PERIOD_START.plusDays(1), "0.001", "0.001", "1000000", "10000", "0", List.of()));
    var input = inputWith(days, ZERO, ZERO);

    var result = calculator.calculate(input);

    assertThat(result.tdGeometric()).isNotNull();
    assertThat(result.scalingFactor()).isNotNull();
  }

  @Test
  void cashDragIsNegativeWhenModelReturnPositive() {
    var days =
        List.of(dailyRecord(PERIOD_START, "0.0008", "0.001", "1000000", "20000", "0", List.of()));
    var input = inputWith(days, ZERO, ZERO);

    var result = calculator.calculate(input);

    assertThat(result.cashDrag()).isNegative();
  }

  @Test
  void cashDragIsPositiveWhenModelReturnNegative() {
    var days =
        List.of(dailyRecord(PERIOD_START, "-0.0012", "-0.001", "1000000", "20000", "0", List.of()));
    var input = inputWith(days, ZERO, ZERO);

    var result = calculator.calculate(input);

    assertThat(result.cashDrag()).isPositive();
  }

  @Test
  void nonSecurityDragExcludesFeeAccruals() {
    // nonSecurityValue = -5000 (e.g. payables exceed receivables, already net of fee accruals)
    var days =
        List.of(
            dailyRecord(PERIOD_START, "0.0008", "0.001", "1000000", "10000", "-5000", List.of()));
    var mgmtFee = new BigDecimal("-0.0000074");
    var input = inputWith(days, mgmtFee, ZERO);

    var result = calculator.calculate(input);

    // nonSecurityDrag = -(nonSecValue/aum) * modelReturn = -(-5000/1000000) * 0.001 = +0.000005
    assertThat(result.nonSecurityDrag()).isPositive();
    // mgmtFeeDrag is separate and negative
    assertThat(result.mgmtFeeDrag()).isNegative();
  }

  @Test
  void normalizedWeightDeviationSumsCorrectly() {
    // Two securities: actual 60/40 but model 50/50
    var sec1 =
        SecurityDailyData.builder()
            .isin("IE001")
            .modelWeight(new BigDecimal("0.50"))
            .actualWeight(new BigDecimal("0.60"))
            .normalizedWeightDiff(new BigDecimal("0.10"))
            .securityReturn(new BigDecimal("0.002"))
            .build();
    var sec2 =
        SecurityDailyData.builder()
            .isin("IE002")
            .modelWeight(new BigDecimal("0.50"))
            .actualWeight(new BigDecimal("0.40"))
            .normalizedWeightDiff(new BigDecimal("-0.10"))
            .securityReturn(new BigDecimal("-0.001"))
            .build();

    var days =
        List.of(
            dailyRecord(
                PERIOD_START, "0.0004", "0.0005", "1000000", "0", "0", List.of(sec1, sec2)));
    var input = inputWith(days, ZERO, ZERO);

    var result = calculator.calculate(input);

    // weight_dev = 0.10 * 0.002 + (-0.10) * (-0.001) = 0.0002 + 0.0001 = 0.0003
    assertThat(result.weightDeviation())
        .isCloseTo(new BigDecimal("0.0003"), within(new BigDecimal("0.0001")));
  }

  @Test
  void zeroAumDayIsSkipped() {
    var days =
        List.of(
            dailyRecord(PERIOD_START, "0.001", "0.001", "0", "0", "0", List.of()),
            dailyRecord(
                PERIOD_START.plusDays(1), "0.001", "0.0012", "1000000", "10000", "0", List.of()));
    var input = inputWith(days, ZERO, ZERO);

    var result = calculator.calculate(input);

    assertThat(result.navEventCount()).isEqualTo(2);
    assertThat(result.avgAum()).isEqualByComparingTo(new BigDecimal("1000000"));
  }

  @Test
  void emptyDailyRecordsProducesZeroResult() {
    var input = inputWith(List.of(), ZERO, ZERO);

    var result = calculator.calculate(input);

    assertThat(result.tdGeometric()).isEqualByComparingTo(ZERO);
    assertThat(result.navEventCount()).isZero();
    assertThat(result.instrumentDetails()).isEmpty();
    assertThat(result.checks()).containsEntry("etfLayerMeasured", false);
  }

  @Test
  void instrumentDetailsArePopulated() {
    var sec =
        SecurityDailyData.builder()
            .isin("IE00BFG1TM61")
            .modelWeight(new BigDecimal("0.295"))
            .actualWeight(new BigDecimal("0.291"))
            .normalizedWeightDiff(new BigDecimal("-0.004"))
            .securityReturn(new BigDecimal("0.003"))
            .build();
    var days =
        List.of(
            dailyRecord(PERIOD_START, "0.001", "0.0012", "1000000", "10000", "0", List.of(sec)));
    var input = inputWith(days, ZERO, ZERO);

    var result = calculator.calculate(input);

    assertThat(result.instrumentDetails()).hasSize(1);
    assertThat(result.instrumentDetails().getFirst().isin()).isEqualTo("IE00BFG1TM61");
  }

  @Test
  void instrumentDetailsCarryInstrumentName() {
    var sec =
        SecurityDailyData.builder()
            .isin("IE00BFG1TM61")
            .instrumentName("iShares Developed World Screened Index Fund")
            .modelWeight(new BigDecimal("0.295"))
            .actualWeight(new BigDecimal("0.291"))
            .normalizedWeightDiff(new BigDecimal("-0.004"))
            .securityReturn(new BigDecimal("0.003"))
            .build();
    var days =
        List.of(
            dailyRecord(PERIOD_START, "0.001", "0.0012", "1000000", "10000", "0", List.of(sec)));
    var input = inputWith(days, ZERO, ZERO);

    var result = calculator.calculate(input);

    assertThat(result.instrumentDetails().getFirst().instrumentName())
        .isEqualTo("iShares Developed World Screened Index Fund");
  }

  @Test
  void instrumentNameFallsBackToIsinWhenMissing() {
    var sec =
        SecurityDailyData.builder()
            .isin("IE00BFG1TM61")
            .modelWeight(new BigDecimal("0.295"))
            .actualWeight(new BigDecimal("0.291"))
            .normalizedWeightDiff(new BigDecimal("-0.004"))
            .securityReturn(new BigDecimal("0.003"))
            .build();
    var days =
        List.of(
            dailyRecord(PERIOD_START, "0.001", "0.0012", "1000000", "10000", "0", List.of(sec)));
    var input = inputWith(days, ZERO, ZERO);

    var result = calculator.calculate(input);

    assertThat(result.instrumentDetails().getFirst().instrumentName()).isEqualTo("IE00BFG1TM61");
  }

  @Test
  void securityReturnCompoundsGeometricallyAcrossDays() {
    // Same security on two days: +1% then +2%. Geometric = 1.01*1.02-1 = 0.0302,
    // which differs from the arithmetic sum 0.03 by the cross term 0.0002.
    var day1Sec =
        SecurityDailyData.builder()
            .isin("IE001")
            .instrumentName("Sec One")
            .modelWeight(new BigDecimal("1.0"))
            .actualWeight(new BigDecimal("1.0"))
            .normalizedWeightDiff(ZERO)
            .securityReturn(new BigDecimal("0.01"))
            .build();
    var day2Sec =
        SecurityDailyData.builder()
            .isin("IE001")
            .instrumentName("Sec One")
            .modelWeight(new BigDecimal("1.0"))
            .actualWeight(new BigDecimal("1.0"))
            .normalizedWeightDiff(ZERO)
            .securityReturn(new BigDecimal("0.02"))
            .build();
    var days =
        List.of(
            dailyRecord(PERIOD_START, "0.001", "0.001", "1000000", "0", "0", List.of(day1Sec)),
            dailyRecord(
                PERIOD_START.plusDays(1), "0.001", "0.001", "1000000", "0", "0", List.of(day2Sec)));
    var input = inputWith(days, ZERO, ZERO);

    var result = calculator.calculate(input);

    assertThat(result.instrumentDetails().getFirst().securityReturn())
        .isEqualByComparingTo(new BigDecimal("0.0302"));
  }

  @Test
  void feeXcheckDetectsDivergence() {
    var days = buildConstantDays(30, "0.0005", "0.0007");
    var mgmtFee = new BigDecimal("-0.00050");
    var input =
        TdAttributionInput.builder()
            .fund(TUK75)
            .periodStart(PERIOD_START)
            .periodEnd(PERIOD_END)
            .periodType(MONTHLY)
            .calendarDays(30)
            .mgmtFeeDragPeriod(mgmtFee)
            .depotFeeDragPeriod(ZERO)
            .expectedAnnualFeeRate(new BigDecimal("0.0027"))
            .dailyRecords(days)
            .build();

    var result = calculator.calculate(input);

    assertThat(result.checks()).containsKey("feeXcheck");
    var feeXcheck = (BigDecimal) result.checks().get("feeXcheck");
    // expected fee drag = -0.0027 * 30/365 = ~-0.000222
    // actual = -0.00050, diff = ~0.000278
    assertThat(feeXcheck).isPositive();
  }

  @Test
  void aReturnAtOrBelowMinusOneHundredPercentFallsBackToACoefficientOfOne() {
    // log1p is undefined at -100%, and a fund cannot lose more than everything. The guard keeps
    // the linking finite rather than producing NaN and poisoning every component.
    var days =
        List.of(
            dailyRecord(PERIOD_START, "-1.5", "0.001", "1000000", "10000", "0", List.of()),
            dailyRecord(
                PERIOD_START.plusDays(1), "0.001", "0.001", "1000000", "10000", "0", List.of()));
    var input = inputWith(days, ZERO, ZERO);

    var result = calculator.calculate(input);

    assertThat(result.scalingFactor()).isNotNull();
    assertThat(result.residual()).isNotNull();
  }

  @Test
  void aPeriodWhoseDaysAllCancelStillLinksWithoutDividingByZero() {
    var days = List.of(dailyRecord(PERIOD_START, "0.000", "0.000", "1000000", "0", "0", List.of()));
    var input = inputWith(days, ZERO, ZERO);

    var result = calculator.calculate(input);

    assertThat(result.scalingFactor()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(result.tdGeometric()).isEqualByComparingTo(ZERO);
  }

  @Test
  void residualToleranceScalesWithTheSquareRootOfThePeriodLength() {
    var annual = new BigDecimal("0.00175");

    var month = toleranceFor(30, annual);
    var quarter = toleranceFor(91, annual);
    var year = toleranceFor(365, annual);

    // The band is a band on noise, and independent daily errors accumulate with the square root of
    // time. That makes the scaling law falsifiable: an observed quarterly residual near sqrt(3)
    // times the monthly one is noise, one near 3 times is a systematic leak and a missing
    // component, which widening the band would hide rather than measure.
    assertThat(year).isEqualByComparingTo(annual);
    var quarterOverMonth = quarter.divide(month, 4, java.math.RoundingMode.HALF_UP).doubleValue();
    assertThat(quarterOverMonth).isCloseTo(Math.sqrt(91.0 / 30.0), within(0.001));
  }

  @Test
  void aResidualInsideTheScaledBandPasses() {
    var input =
        toleranceInput(buildConstantDays(30, "0.0005", "0.0005"), new BigDecimal("0.00175"));

    var result = calculator.calculate(input);

    assertThat(result.checks()).containsEntry("residualWithinTolerance", true);
    assertThat(result.checks()).containsEntry("residualToleranceBps", new BigDecimal("5.02"));
  }

  @Test
  void aResidualOutsideTheScaledBandFails() {
    var input =
        toleranceInput(buildConstantDays(30, "0.0015", "0.0005"), new BigDecimal("0.00175"));

    var result = calculator.calculate(input);

    assertThat(result.checks()).containsEntry("residualWithinTolerance", false);
  }

  @Test
  void anUnconfiguredToleranceLeavesNoVerdictRatherThanAPass() {
    var input = toleranceInput(buildConstantDays(30, "0.0015", "0.0005"), null);

    var result = calculator.calculate(input);

    // Storing true here would stamp "checked and within tolerance" on every period that predates
    // the parameter - the backfill's first act - and read exactly like a measured pass.
    assertThat(result.checks())
        .doesNotContainKey("residualWithinTolerance")
        .doesNotContainKey("residualToleranceBps");
  }

  private BigDecimal toleranceFor(int calendarDays, BigDecimal annual) {
    return java.util.Objects.requireNonNull(
        TdAttributionCalculator.scaledResidualTolerance(
            toleranceInput(buildConstantDays(1, "0", "0"), annual, calendarDays)));
  }

  private TdAttributionInput toleranceInput(List<DailyRecord> days, BigDecimal annual) {
    return toleranceInput(days, annual, 30);
  }

  private TdAttributionInput toleranceInput(
      List<DailyRecord> days, BigDecimal annual, int calendarDays) {
    return TdAttributionInput.builder()
        .fund(TUK75)
        .periodStart(PERIOD_START)
        .periodEnd(PERIOD_END)
        .periodType(MONTHLY)
        .calendarDays(calendarDays)
        .residualTolerance(annual)
        .dailyRecords(days)
        .build();
  }

  @Test
  void surfacesSeriesGapDaysInChecks() {
    var days = buildConstantDays(5, "0.0005", "0.0005");
    var input =
        TdAttributionInput.builder()
            .fund(TUK75)
            .periodStart(PERIOD_START)
            .periodEnd(PERIOD_END)
            .periodType(MONTHLY)
            .calendarDays(30)
            .seriesGapDays(2)
            .dailyRecords(days)
            .build();

    var result = calculator.calculate(input);

    assertThat(result.checks()).containsEntry("seriesGapDays", 2);
  }

  @Test
  void modelPortfolioVersionChangeMidPeriod() {
    // Day 1: model has sec1=60%, sec2=40%
    // Day 2: model changes to sec1=50%, sec3=50% (sec2 removed, sec3 added)
    var day1Secs =
        List.of(
            SecurityDailyData.builder()
                .isin("SEC1")
                .modelWeight(new BigDecimal("0.60"))
                .actualWeight(new BigDecimal("0.58"))
                .normalizedWeightDiff(new BigDecimal("-0.02"))
                .securityReturn(new BigDecimal("0.002"))
                .build(),
            SecurityDailyData.builder()
                .isin("SEC2")
                .modelWeight(new BigDecimal("0.40"))
                .actualWeight(new BigDecimal("0.42"))
                .normalizedWeightDiff(new BigDecimal("0.02"))
                .securityReturn(new BigDecimal("-0.001"))
                .build());
    var day2Secs =
        List.of(
            SecurityDailyData.builder()
                .isin("SEC1")
                .modelWeight(new BigDecimal("0.50"))
                .actualWeight(new BigDecimal("0.55"))
                .normalizedWeightDiff(new BigDecimal("0.05"))
                .securityReturn(new BigDecimal("0.003"))
                .build(),
            SecurityDailyData.builder()
                .isin("SEC2")
                .modelWeight(ZERO)
                .actualWeight(new BigDecimal("0.40"))
                .normalizedWeightDiff(new BigDecimal("0.40"))
                .securityReturn(new BigDecimal("0.001"))
                .build(),
            SecurityDailyData.builder()
                .isin("SEC3")
                .modelWeight(new BigDecimal("0.50"))
                .actualWeight(new BigDecimal("0.05"))
                .normalizedWeightDiff(new BigDecimal("-0.45"))
                .securityReturn(new BigDecimal("0.004"))
                .build());

    var days =
        List.of(
            dailyRecord(PERIOD_START, "0.0009", "0.001", "1000000", "10000", "0", day1Secs),
            dailyRecord(
                PERIOD_START.plusDays(1), "0.0015", "0.002", "1000000", "10000", "0", day2Secs));
    var input = inputWith(days, ZERO, ZERO);

    var result = calculator.calculate(input);

    // SEC2 appears in details (transition instrument with model=0 on day 2)
    assertThat(result.instrumentDetails()).extracting("isin").contains("SEC1", "SEC2", "SEC3");
    // Components still sum to geometric TD
    var componentSum =
        result
            .mgmtFeeDrag()
            .add(result.depotFeeDrag())
            .add(result.cashDrag())
            .add(result.nonSecurityDrag())
            .add(result.weightDeviation())
            .add(result.transactionCosts())
            .add(result.residual());
    assertThat(componentSum).isCloseTo(result.tdGeometric(), within(new BigDecimal("0.00000001")));
  }

  // --- helpers ---

  // Independent reference implementation of the Cariño coefficient for cross-checking the
  // production carinoCoefficient()/linkDaily() math in tests.
  private static double referenceCarino(double r, double b) {
    if (Math.abs(r - b) < 1e-12) {
      return 1.0 / (1.0 + r);
    }
    return (Math.log1p(r) - Math.log1p(b)) / (r - b);
  }

  private List<DailyRecord> buildConstantDays(
      int count, String fundReturnStr, String modelReturnStr) {
    var days = new ArrayList<DailyRecord>();
    for (int i = 0; i < count; i++) {
      days.add(
          dailyRecord(
              PERIOD_START.plusDays(i),
              fundReturnStr,
              modelReturnStr,
              "1000000",
              "10000",
              "0",
              List.of()));
    }
    return days;
  }

  private List<DailyRecord> buildDaysWithComponents(int count) {
    var days = new ArrayList<DailyRecord>();
    for (int i = 0; i < count; i++) {
      var sec =
          SecurityDailyData.builder()
              .isin("IE001")
              .modelWeight(new BigDecimal("1.0"))
              .actualWeight(new BigDecimal("0.98"))
              .normalizedWeightDiff(new BigDecimal("-0.02"))
              .securityReturn(new BigDecimal("0.001"))
              .build();
      days.add(
          dailyRecord(
              PERIOD_START.plusDays(i),
              "0.0008",
              "0.001",
              "1000000",
              "15000",
              "2000",
              List.of(sec)));
    }
    return days;
  }

  private DailyRecord dailyRecord(
      LocalDate date,
      String fundReturn,
      String modelReturn,
      String aum,
      String cash,
      String nonSec,
      List<SecurityDailyData> securities) {
    return DailyRecord.builder()
        .date(date)
        .fundReturn(new BigDecimal(fundReturn))
        .modelReturn(new BigDecimal(modelReturn))
        .aum(new BigDecimal(aum))
        .cashValue(new BigDecimal(cash))
        .nonSecurityValue(new BigDecimal(nonSec))
        .securities(securities)
        .build();
  }

  @Test
  void averageWeightsUseThePeriodDayCountSoAnInstrumentPresentHalfTheMonthIsDilutedNotInflated() {
    var held =
        SecurityDailyData.builder()
            .isin("IE001")
            .instrumentName("Held all month")
            .modelWeight(new BigDecimal("0.50"))
            .actualWeight(new BigDecimal("0.50"))
            .normalizedWeightDiff(ZERO)
            .securityReturn(ZERO)
            .build();
    var enteredLate =
        SecurityDailyData.builder()
            .isin("IE002")
            .instrumentName("Entered on the second day")
            .modelWeight(new BigDecimal("0.50"))
            .actualWeight(new BigDecimal("0.50"))
            .normalizedWeightDiff(ZERO)
            .securityReturn(ZERO)
            .build();

    var days =
        List.of(
            dailyRecord(PERIOD_START, "0", "0", "1000000", "0", "0", List.of(held)),
            dailyRecord(
                PERIOD_START.plusDays(1),
                "0",
                "0",
                "1000000",
                "0",
                "0",
                List.of(held, enteredLate)));

    var result = calculator.calculate(inputWith(days, ZERO, ZERO));

    var lateComer =
        result.instrumentDetails().stream()
            .filter(detail -> detail.isin().equals("IE002"))
            .findFirst()
            .orElseThrow();
    assertThat(lateComer.avgActualWeight()).isEqualByComparingTo(new BigDecimal("0.25"));
    assertThat(lateComer.modelWeight()).isEqualByComparingTo(new BigDecimal("0.25"));
  }

  @Test
  void averageActualWeightsSumToOneAcrossAPeriodWithAnEntryAndAnExit() {
    var exiting =
        SecurityDailyData.builder()
            .isin("IE001")
            .modelWeight(new BigDecimal("1.00"))
            .actualWeight(BigDecimal.ONE)
            .normalizedWeightDiff(ZERO)
            .securityReturn(ZERO)
            .build();
    var entering =
        SecurityDailyData.builder()
            .isin("IE002")
            .modelWeight(new BigDecimal("1.00"))
            .actualWeight(BigDecimal.ONE)
            .normalizedWeightDiff(ZERO)
            .securityReturn(ZERO)
            .build();

    var days =
        List.of(
            dailyRecord(PERIOD_START, "0", "0", "1000000", "0", "0", List.of(exiting)),
            dailyRecord(
                PERIOD_START.plusDays(1), "0", "0", "1000000", "0", "0", List.of(entering)));

    var result = calculator.calculate(inputWith(days, ZERO, ZERO));

    var summedActualWeight =
        result.instrumentDetails().stream()
            .map(TdAttributionResult.InstrumentAttribution::avgActualWeight)
            .reduce(ZERO, BigDecimal::add);
    assertThat(summedActualWeight).isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void securityReturnStaysCompoundedOverTheDaysTheInstrumentWasHeld() {
    var present =
        SecurityDailyData.builder()
            .isin("IE002")
            .modelWeight(new BigDecimal("0.50"))
            .actualWeight(new BigDecimal("0.50"))
            .normalizedWeightDiff(ZERO)
            .securityReturn(new BigDecimal("0.10"))
            .build();

    var days =
        List.of(
            dailyRecord(PERIOD_START, "0", "0", "1000000", "0", "0", List.of()),
            dailyRecord(PERIOD_START.plusDays(1), "0", "0", "1000000", "0", "0", List.of(present)));

    var result = calculator.calculate(inputWith(days, ZERO, ZERO));

    var detail =
        result.instrumentDetails().stream()
            .filter(d -> d.isin().equals("IE002"))
            .findFirst()
            .orElseThrow();
    assertThat(detail.securityReturn()).isEqualByComparingTo(new BigDecimal("0.10"));
  }

  @Test
  void reportsHowManyDaysCouldNotBeAttributedSoAnInflatedResidualIsNotReadAsAQuietPeriod() {
    var days =
        List.of(
            dailyRecord(PERIOD_START, "0.001", "0.001", "1000000", "0", "0", List.of()),
            dailyRecord(PERIOD_START.plusDays(1), "0.001", "0.001", "0", "0", "0", List.of()),
            dailyRecord(
                PERIOD_START.plusDays(2), "0.001", "0.001", "1000000", "0", "0", List.of()));

    var result = calculator.calculate(inputWith(days, ZERO, ZERO));

    assertThat(result.checks()).containsEntry("attributedDays", 2);
    assertThat(result.checks()).containsEntry("unattributedDays", 1);
    assertThat(result.navEventCount()).isEqualTo(3);
  }

  @Test
  void reportsNoUnattributedDaysForACleanPeriod() {
    var days =
        List.of(
            dailyRecord(PERIOD_START, "0.001", "0.001", "1000000", "0", "0", List.of()),
            dailyRecord(
                PERIOD_START.plusDays(1), "0.001", "0.001", "1000000", "0", "0", List.of()));

    var result = calculator.calculate(inputWith(days, ZERO, ZERO));

    assertThat(result.checks()).containsEntry("attributedDays", 2);
    assertThat(result.checks()).containsEntry("unattributedDays", 0);
  }

  @Test
  void anIndexBenchmarkedHoldingsOwnOcfIsSplitOutOfTheMeasuredSumRatherThanAddedToIt() {
    var days = buildConstantDays(20, "0.0005", "0.0005");
    var etfOcfDrag = new BigDecimal("-0.00016438");
    var benchmarkModelSum = new BigDecimal("-0.00025000");
    var input = etfLayerInput(days, etfOcfDrag, benchmarkModelSum, ZERO);

    var result = calculator.calculate(input);

    assertThat(result.etfTrackingResidual())
        .isEqualByComparingTo(benchmarkModelSum.subtract(etfOcfDrag));
    assertThat(result.tdVsBenchmark())
        .isEqualByComparingTo(result.tdGeometric().add(benchmarkModelSum));
  }

  @Test
  void proxyBenchmarkedHoldingsHaveTheProxyOwnOcfAddedBackToReachTheIndex() {
    var days = buildConstantDays(20, "0.0005", "0.0005");
    var etfOcfDrag = new BigDecimal("-0.00016438");
    var benchmarkModelSum = new BigDecimal("-0.00025000");
    var proxyOcfDrag = new BigDecimal("-0.00010000");

    var result =
        calculator.calculate(etfLayerInput(days, etfOcfDrag, benchmarkModelSum, proxyOcfDrag));

    var modelVsIndex = benchmarkModelSum.add(proxyOcfDrag);
    assertThat(result.tdVsBenchmark()).isEqualByComparingTo(result.tdGeometric().add(modelVsIndex));
    assertThat(result.etfOcfDrag().add(result.etfTrackingResidual()))
        .isEqualByComparingTo(modelVsIndex);
  }

  @Test
  void anUnmeasuredEtfLayerReportsZeroRatherThanFabricatingOutperformance() {
    var days = buildConstantDays(20, "0.0005", "0.0005");

    var result =
        calculator.calculate(etfLayerInput(days, new BigDecimal("-0.00016438"), null, null));

    assertThat(result.etfOcfDrag()).isEqualByComparingTo(ZERO);
    assertThat(result.etfTrackingResidual()).isEqualByComparingTo(ZERO);
    assertThat(result.tdVsBenchmark()).isEqualByComparingTo(result.tdGeometric());
    assertThat(result.checks()).containsEntry("etfLayerMeasured", false);
  }

  private TdAttributionInput etfLayerInput(
      List<DailyRecord> days,
      BigDecimal etfOcfDrag,
      BigDecimal benchmarkModelSum,
      BigDecimal proxyOcfDrag) {
    return TdAttributionInput.builder()
        .fund(TUK75)
        .periodStart(PERIOD_START)
        .periodEnd(PERIOD_END)
        .periodType(MONTHLY)
        .calendarDays(30)
        .mgmtFeeDragPeriod(ZERO)
        .depotFeeDragPeriod(ZERO)
        .expectedAnnualFeeRate(new BigDecimal("0.0027"))
        .etfOcfDragPeriod(etfOcfDrag)
        .benchmarkModelSumPeriod(benchmarkModelSum)
        .benchmarkProxyOcfDragPeriod(proxyOcfDrag)
        .dailyRecords(days)
        .build();
  }

  private TdAttributionInput inputWith(
      List<DailyRecord> days, BigDecimal mgmtFee, BigDecimal depotFee) {
    return TdAttributionInput.builder()
        .fund(TUK75)
        .periodStart(PERIOD_START)
        .periodEnd(PERIOD_END)
        .periodType(MONTHLY)
        .calendarDays(30)
        .mgmtFeeDragPeriod(mgmtFee)
        .depotFeeDragPeriod(depotFee)
        .expectedAnnualFeeRate(new BigDecimal("0.0027"))
        .dailyRecords(days)
        .build();
  }
}
