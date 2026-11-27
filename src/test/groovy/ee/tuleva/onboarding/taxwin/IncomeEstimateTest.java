package ee.tuleva.onboarding.taxwin;

import static ee.tuleva.onboarding.taxwin.ExpectedIncomeBasis.NO_DATA;
import static ee.tuleva.onboarding.taxwin.ExpectedIncomeBasis.RECENT_MONTHS;
import static ee.tuleva.onboarding.taxwin.ExpectedIncomeSource.ESTIMATED;
import static java.math.BigDecimal.ZERO;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class IncomeEstimateTest {

  @Test
  void spreadsWhatIsLeftOfTheYearlyIncomeEvenlyOverTheMonthsNotDeclaredYet() {
    var estimate =
        new IncomeEstimate(
            new BigDecimal("25000.00"),
            new BigDecimal("900.00"),
            YearMonth.of(2026, 10),
            List.of(estimated(YearMonth.of(2026, 11)), estimated(YearMonth.of(2026, 12))),
            RECENT_MONTHS);

    assertThat(estimate.spreadOverExpectedMonths(new BigDecimal("33000")))
        .isEqualTo(
            Map.of(
                YearMonth.of(2026, 11), new BigDecimal("4000.00"),
                YearMonth.of(2026, 12), new BigDecimal("4000.00")));
  }

  @Test
  void theCentsLeftOverFromAnUnevenSplitGoToTheLastMonth() {
    var months = IntStream.rangeClosed(1, 12).mapToObj(month -> YearMonth.of(2026, month)).toList();
    var estimate =
        new IncomeEstimate(
            ZERO, ZERO, null, months.stream().map(IncomeEstimateTest::estimated).toList(), NO_DATA);

    assertThat(estimate.spreadOverExpectedMonths(new BigDecimal("10000")))
        .isEqualTo(
            months.stream()
                .collect(
                    toMap(
                        identity(),
                        month ->
                            new BigDecimal(
                                month.equals(YearMonth.of(2026, 12)) ? "833.37" : "833.33"))));
  }

  private static ExpectedMonthlyIncome estimated(YearMonth month) {
    return new ExpectedMonthlyIncome(month, new BigDecimal("2500.00"), ESTIMATED);
  }
}
