package ee.tuleva.onboarding.taxwin;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
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
}
