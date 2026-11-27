package ee.tuleva.onboarding.taxwin;

import static ee.tuleva.onboarding.taxwin.ExpectedIncomeBasis.LAST_YEAR;
import static ee.tuleva.onboarding.taxwin.ExpectedIncomeBasis.NO_DATA;
import static ee.tuleva.onboarding.taxwin.ExpectedIncomeBasis.RECENT_MONTHS;
import static ee.tuleva.onboarding.taxwin.ExpectedIncomeSource.CORRECTED;
import static ee.tuleva.onboarding.taxwin.ExpectedIncomeSource.ESTIMATED;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.DOWN;
import static java.util.Comparator.reverseOrder;

import java.math.BigDecimal;
import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

class IncomeEstimator {

  private static final int RECENT_MONTHS_TO_AVERAGE = 3;

  IncomeEstimate estimate(
      YearMonth currentMonth,
      Map<YearMonth, DeclaredMonth> declared,
      Map<YearMonth, BigDecimal> corrections) {
    int year = currentMonth.getYear();
    List<YearMonth> declaredThisYear = monthsOf(year, declared);
    List<YearMonth> declaredLastYear = monthsOf(year - 1, declared);
    @Nullable YearMonth knownThrough =
        declaredThisYear.isEmpty() ? null : declaredThisYear.getFirst();
    ExpectedIncomeBasis basis =
        !declaredThisYear.isEmpty()
            ? RECENT_MONTHS
            : !declaredLastYear.isEmpty() ? LAST_YEAR : NO_DATA;
    Function<YearMonth, BigDecimal> estimateFor =
        switch (basis) {
          case RECENT_MONTHS -> {
            BigDecimal recentAverage =
                average(
                    declaredThisYear.stream().limit(RECENT_MONTHS_TO_AVERAGE).toList(), declared);
            yield month -> recentAverage;
          }
          case LAST_YEAR -> {
            BigDecimal lastYearAverage = average(declaredLastYear, declared);
            yield month -> {
              DeclaredMonth sameMonthLastYear = declared.get(month.minusYears(1));
              return sameMonthLastYear == null ? lastYearAverage : sameMonthLastYear.income();
            };
          }
          case NO_DATA -> month -> ZERO;
        };
    YearMonth firstExpected =
        knownThrough == null ? YearMonth.of(year, Month.JANUARY) : knownThrough.plusMonths(1);
    List<ExpectedMonthlyIncome> expected =
        Stream.iterate(
                firstExpected, month -> month.getYear() == year, month -> month.plusMonths(1))
            .map(
                month ->
                    corrections.containsKey(month)
                        ? new ExpectedMonthlyIncome(month, corrections.get(month), CORRECTED)
                        : new ExpectedMonthlyIncome(month, estimateFor.apply(month), ESTIMATED))
            .toList();
    return new IncomeEstimate(
        sum(declaredThisYear, declared, DeclaredMonth::income),
        sum(declaredThisYear, declared, DeclaredMonth::withheldContributions),
        knownThrough,
        expected,
        basis);
  }

  private static List<YearMonth> monthsOf(int year, Map<YearMonth, DeclaredMonth> declared) {
    return declared.keySet().stream()
        .filter(month -> month.getYear() == year)
        .sorted(reverseOrder())
        .toList();
  }

  private static BigDecimal average(
      List<YearMonth> months, Map<YearMonth, DeclaredMonth> declared) {
    return sum(months, declared, DeclaredMonth::income)
        .divide(BigDecimal.valueOf(months.size()), 2, DOWN);
  }

  private static BigDecimal sum(
      List<YearMonth> months,
      Map<YearMonth, DeclaredMonth> declared,
      Function<DeclaredMonth, BigDecimal> amount) {
    return months.stream().map(declared::get).map(amount).reduce(ZERO, BigDecimal::add);
  }
}
