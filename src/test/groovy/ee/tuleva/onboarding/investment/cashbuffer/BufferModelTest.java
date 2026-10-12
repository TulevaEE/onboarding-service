package ee.tuleva.onboarding.investment.cashbuffer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BufferModelTest {

  private static final int TWO_BUSINESS_DAYS = 2;

  private static final BufferModel MODEL =
      new BufferModel(
          new BigDecimal("0.95"), new BigDecimal("0.20"), new BigDecimal("0.1"), TWO_BUSINESS_DAYS);

  @Test
  void recommendsTheSoftLimitFromTheMonthlyTailAndTheHardLimitFromTheSettlementHorizon() {
    var window =
        window(
            month(1, "900000.00", "30000.00"),
            month(2, "800000.00", "72000.00"),
            month(3, "0.00", "0.00"),
            month(4, "1000000.00", "15000.00"));

    var recommendation =
        MODEL.recommend(
            window,
            businessDays("1000.00", "0.00", "3000.00", "500.00"),
            new BigDecimal("3000.00"));

    assertThat(recommendation)
        .isEqualTo(
            new Recommendation(
                MODEL,
                new BigDecimal("65700.00"),
                new BigDecimal("480000.00"),
                new BigDecimal("3450.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("20700.00"),
                new BigDecimal("6450.00")));
  }

  @Test
  void theHardLimitCoversTheOutflowOfTheWorstRunOfBusinessDaysOneSaleTakesToSettle() {
    var window = window(month(1, "0.00", "10000.00"), month(2, "0.00", "10000.00"));

    var recommendation =
        MODEL.recommend(
            window,
            businessDays("0.00", "8000.00", "2000.00", "0.00", "0.00"),
            new BigDecimal("100.00"));

    assertThat(recommendation)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new Recommendation(
                MODEL,
                new BigDecimal("10000"),
                BigDecimal.ZERO,
                new BigDecimal("9700"),
                new BigDecimal("100"),
                new BigDecimal("10100"),
                new BigDecimal("9800")));
  }

  @Test
  void theSoftLimitNeverSitsBelowTheHardOne() {
    var window = window(month(1, "5000000.00", "1000.00"), month(2, "5000000.00", "2000.00"));

    var recommendation =
        MODEL.recommend(window, businessDays("40000.00", "0.00"), new BigDecimal("300.00"));

    assertThat(recommendation)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new Recommendation(
                MODEL,
                new BigDecimal("1950"),
                new BigDecimal("5000000"),
                new BigDecimal("40000"),
                new BigDecimal("300"),
                new BigDecimal("40300"),
                new BigDecimal("40300")));
  }

  @Test
  void withNoInflowCreditTheWholeMonthlyTailIsHeld() {
    var noCredit =
        new BufferModel(
            new BigDecimal("0.95"), new BigDecimal("0.20"), BigDecimal.ZERO, TWO_BUSINESS_DAYS);
    var window = window(month(1, "5000000.00", "1000.00"), month(2, "5000000.00", "2000.00"));

    var recommendation =
        noCredit.recommend(window, businessDays("0.00", "0.00"), new BigDecimal("300.00"));

    assertThat(recommendation)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new Recommendation(
                noCredit,
                new BigDecimal("1950"),
                new BigDecimal("5000000"),
                BigDecimal.ZERO,
                new BigDecimal("300"),
                new BigDecimal("2250"),
                new BigDecimal("300")));
  }

  private static FlowWindow window(MonthlyFlows... months) {
    return new FlowWindow(List.of(months));
  }

  private static BusinessDayOutflows businessDays(String... outflows) {
    return new BusinessDayOutflows(Arrays.stream(outflows).map(BigDecimal::new).toList());
  }

  private static MonthlyFlows month(int month, String inflow, String tailOutflow) {
    var zero = new BigDecimal("0.00");
    return new MonthlyFlows(
        YearMonth.of(2026, month),
        new BigDecimal(inflow),
        zero,
        new BigDecimal(tailOutflow),
        new BigDecimal("999999.00"),
        zero,
        0);
  }
}
