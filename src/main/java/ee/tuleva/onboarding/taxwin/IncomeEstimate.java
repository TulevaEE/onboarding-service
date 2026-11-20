package ee.tuleva.onboarding.taxwin;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.DOWN;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record IncomeEstimate(
    BigDecimal knownIncome,
    BigDecimal knownContributions,
    @Nullable YearMonth incomeKnownThrough,
    List<ExpectedMonthlyIncome> expectedIncome,
    ExpectedIncomeBasis basis) {

  BigDecimal expectedTotal() {
    return expectedIncome.stream().map(ExpectedMonthlyIncome::amount).reduce(ZERO, BigDecimal::add);
  }

  Map<YearMonth, BigDecimal> spreadOverExpectedMonths(BigDecimal yearlyIncome) {
    BigDecimal restOfTheYear = yearlyIncome.subtract(knownIncome);
    BigDecimal months = BigDecimal.valueOf(expectedIncome.size());
    BigDecimal perMonth = restOfTheYear.divide(months, 2, DOWN);
    BigDecimal lastMonth = restOfTheYear.subtract(perMonth.multiply(months.subtract(ONE)));
    YearMonth last = expectedIncome.getLast().month();
    return expectedIncome.stream()
        .map(ExpectedMonthlyIncome::month)
        .collect(toMap(identity(), month -> month.equals(last) ? lastMonth : perMonth));
  }
}
