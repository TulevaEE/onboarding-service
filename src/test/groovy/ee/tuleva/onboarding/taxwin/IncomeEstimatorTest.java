package ee.tuleva.onboarding.taxwin;

import static ee.tuleva.onboarding.taxwin.ExpectedIncomeBasis.LAST_YEAR;
import static ee.tuleva.onboarding.taxwin.ExpectedIncomeBasis.NO_DATA;
import static ee.tuleva.onboarding.taxwin.ExpectedIncomeBasis.RECENT_MONTHS;
import static ee.tuleva.onboarding.taxwin.ExpectedIncomeSource.CORRECTED;
import static ee.tuleva.onboarding.taxwin.ExpectedIncomeSource.ESTIMATED;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class IncomeEstimatorTest {

  private final IncomeEstimator estimator = new IncomeEstimator();

  @Test
  void estimatesTheRestOfTheYearFromTheAverageOfTheLastThreeDeclaredMonths() {
    var declared = salaries(YearMonth.of(2026, 1), YearMonth.of(2026, 7), "2000.00");
    declared.put(YearMonth.of(2026, 8), month("2000.00"));
    declared.put(YearMonth.of(2026, 9), month("2500.00"));
    declared.put(YearMonth.of(2026, 10), month("3001.00"));

    var estimate = estimator.estimate(YearMonth.of(2026, 11), declared, Map.of());

    assertThat(estimate)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new IncomeEstimate(
                new BigDecimal("21501.00"),
                new BigDecimal("774.036"),
                YearMonth.of(2026, 10),
                List.of(
                    expected(2026, 11, "2500.33", ESTIMATED),
                    expected(2026, 12, "2500.33", ESTIMATED)),
                RECENT_MONTHS));
  }

  @Test
  void aMonthNotYetDeclaredIsEstimatedEvenWhenItHasPassed() {
    var declared = salaries(YearMonth.of(2026, 4), YearMonth.of(2027, 1), "1800.00");

    var estimate = estimator.estimate(YearMonth.of(2027, 3), declared, Map.of());

    assertThat(estimate.incomeKnownThrough()).isEqualTo(YearMonth.of(2027, 1));
    assertThat(estimate.expectedIncome())
        .extracting(ExpectedMonthlyIncome::month)
        .first()
        .isEqualTo(YearMonth.of(2027, 2));
    assertThat(estimate.expectedIncome()).hasSize(11);
  }

  @Test
  void inJanuaryEachMonthIsEstimatedFromTheSameMonthLastYear() {
    var declared = salaries(YearMonth.of(2026, 2), YearMonth.of(2026, 11), "2200.00");
    declared.put(YearMonth.of(2026, 1), month("1000.00"));

    var estimate = estimator.estimate(YearMonth.of(2027, 1), declared, Map.of());

    assertThat(estimate.basis()).isEqualTo(LAST_YEAR);
    assertThat(estimate.knownIncome()).isEqualByComparingTo(ZERO);
    assertThat(estimate.incomeKnownThrough()).isNull();
    assertThat(estimate.expectedIncome())
        .usingRecursiveFieldByFieldElementComparator()
        .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .contains(
            expected(2027, 1, "1000.00", ESTIMATED),
            expected(2027, 6, "2200.00", ESTIMATED),
            expected(2027, 12, "2090.90", ESTIMATED))
        .hasSize(12);
  }

  @Test
  void withNothingDeclaredEveryMonthOfTheYearIsLeftForThePersonToFillIn() {
    var estimate = estimator.estimate(YearMonth.of(2026, 11), Map.of(), Map.of());

    assertThat(estimate)
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            new IncomeEstimate(
                ZERO,
                ZERO,
                null,
                IntStream.rangeClosed(1, 12)
                    .mapToObj(month -> expected(2026, month, "0", ESTIMATED))
                    .toList(),
                NO_DATA));
  }

  @Test
  void aCorrectionReplacesTheEstimateForAMonthNotYetDeclared() {
    var declared = salaries(YearMonth.of(2025, 11), YearMonth.of(2026, 10), "2500.00");

    var estimate =
        estimator.estimate(
            YearMonth.of(2026, 11),
            declared,
            Map.of(YearMonth.of(2026, 12), new BigDecimal("12500.00")));

    assertThat(estimate.expectedIncome())
        .usingRecursiveFieldByFieldElementComparator()
        .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .containsExactly(
            expected(2026, 11, "2500.00", ESTIMATED), expected(2026, 12, "12500.00", CORRECTED));
    assertThat(estimate.expectedTotal()).isEqualByComparingTo("15000.00");
  }

  @Test
  void aCorrectionForADeclaredMonthGivesWayToWhatWasDeclared() {
    var declared = salaries(YearMonth.of(2025, 11), YearMonth.of(2026, 10), "2500.00");

    var estimate =
        estimator.estimate(
            YearMonth.of(2026, 11),
            declared,
            Map.of(YearMonth.of(2026, 10), new BigDecimal("9000.00")));

    assertThat(estimate.knownIncome()).isEqualByComparingTo("25000.00");
  }

  private static Map<YearMonth, DeclaredMonth> salaries(
      YearMonth from, YearMonth through, String amount) {
    return new HashMap<>(
        Stream.iterate(from, month -> !month.isAfter(through), month -> month.plusMonths(1))
            .collect(toMap(month -> month, month -> month(amount))));
  }

  private static DeclaredMonth month(String income) {
    var amount = new BigDecimal(income);
    return new DeclaredMonth(amount, amount.multiply(new BigDecimal("0.036")));
  }

  private static ExpectedMonthlyIncome expected(
      int year, int month, String amount, ExpectedIncomeSource source) {
    return new ExpectedMonthlyIncome(YearMonth.of(year, month), new BigDecimal(amount), source);
  }
}
