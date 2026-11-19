package ee.tuleva.onboarding.taxwin;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class IncomeExpectations {

  private static final BigDecimal LARGEST_EXPECTED_MONTHLY_INCOME = new BigDecimal("100000");
  private static final int CENTS = 2;

  private final TaxIncomeStore incomeStore;
  private final IncomeEstimator estimator = new IncomeEstimator();

  IncomeEstimate estimate(Long userId, YearMonth currentMonth) {
    return estimator.estimate(
        currentMonth, incomeStore.declared(userId), incomeStore.corrections(userId));
  }

  void expectMonthlyIncome(
      Long userId, YearMonth currentMonth, YearMonth month, BigDecimal amount) {
    if (month.getYear() != currentMonth.getYear()
        || incomeStore.declared(userId).containsKey(month)
        || !isValidMonthlyIncome(amount)) {
      throw new InvalidExpectedIncomeException(month);
    }
    incomeStore.correct(userId, month, amount);
  }

  void expectYearlyIncome(Long userId, YearMonth currentMonth, BigDecimal yearlyIncome) {
    Map<YearMonth, BigDecimal> expected =
        spread(userId, currentMonth, yearlyIncome)
            .orElseThrow(() -> new InvalidExpectedIncomeException(currentMonth.getYear()));
    incomeStore.expectYearlyIncome(
        userId, new YearlyIncome(currentMonth.getYear(), yearlyIncome), expected);
  }

  void spreadYearlyIncomeAgain(Long userId, YearMonth currentMonth) {
    incomeStore
        .yearlyIncome(userId)
        .ifPresent(
            yearlyIncome ->
                Optional.of(yearlyIncome)
                    .filter(income -> income.isFor(currentMonth.getYear()))
                    .flatMap(income -> spread(userId, currentMonth, income.amount()))
                    .ifPresentOrElse(
                        expected -> incomeStore.expectYearlyIncome(userId, yearlyIncome, expected),
                        () -> incomeStore.forgetYearlyIncome(userId)));
  }

  private Optional<Map<YearMonth, BigDecimal>> spread(
      Long userId, YearMonth currentMonth, BigDecimal yearlyIncome) {
    Map<YearMonth, BigDecimal> expected =
        estimate(userId, currentMonth).spreadOverExpectedMonths(yearlyIncome);
    return expected.values().stream().allMatch(IncomeExpectations::isValidMonthlyIncome)
        ? Optional.of(expected)
        : Optional.empty();
  }

  private static boolean isValidMonthlyIncome(BigDecimal amount) {
    return amount.signum() >= 0
        && amount.compareTo(LARGEST_EXPECTED_MONTHLY_INCOME) <= 0
        && amount.stripTrailingZeros().scale() <= CENTS;
  }
}
