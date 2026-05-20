package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.tracking.PeriodType.MONTHLY;
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

    assertThat(result.businessDays()).isEqualTo(2);
    assertThat(result.avgAum()).isEqualByComparingTo(new BigDecimal("1000000"));
  }

  @Test
  void emptyDailyRecordsProducesZeroResult() {
    var input = inputWith(List.of(), ZERO, ZERO);

    var result = calculator.calculate(input);

    assertThat(result.tdGeometric()).isEqualByComparingTo(ZERO);
    assertThat(result.businessDays()).isZero();
    assertThat(result.instrumentDetails()).isEmpty();
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
