package ee.tuleva.onboarding.taxwin;

import static ee.tuleva.onboarding.taxwin.LimitCertainty.CERTAIN;
import static ee.tuleva.onboarding.taxwin.LimitCertainty.ESTIMATED;
import static ee.tuleva.onboarding.taxwin.PaymentSuggestion.RECURRING;
import static ee.tuleva.onboarding.taxwin.PaymentSuggestion.SINGLE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.DOWN;
import static java.math.RoundingMode.FLOOR;

import java.math.BigDecimal;

class TaxHeadroomCalculator {

  private static final BigDecimal LIMIT_SHARE_OF_GROSS_INCOME = new BigDecimal("0.15");
  private static final BigDecimal LIMIT_CAP = new BigDecimal("6000");
  private static final BigDecimal YEARLY_TAX_FREE_INCOME = new BigDecimal("8400");
  private static final BigDecimal UNEMPLOYMENT_INSURANCE_AND_SECOND_PILLAR_SHARE =
      new BigDecimal("0.036");
  private static final BigDecimal INCOME_TAX_RATE = new BigDecimal("0.22");
  private static final BigDecimal MONTHS_IN_YEAR = new BigDecimal("12");
  private static final int MONTHS_LEFT_FOR_A_RECURRING_PAYMENT = 3;

  HeadroomFigures calculate(
      BigDecimal knownIncome,
      BigDecimal knownDeductions,
      BigDecimal expectedIncome,
      BigDecimal paid,
      int monthsLeft) {
    BigDecimal income = knownIncome.add(expectedIncome);
    BigDecimal deductions =
        knownDeductions.add(
            expectedIncome.multiply(UNEMPLOYMENT_INSURANCE_AND_SECOND_PILLAR_SHARE));
    BigDecimal limit = limitFor(income, deductions);
    BigDecimal remaining = limit.subtract(paid).max(ZERO);
    BigDecimal monthlyPayment =
        remaining
            .divide(BigDecimal.valueOf(Math.max(monthsLeft, 1)), 2, DOWN)
            .min(limit.divide(MONTHS_IN_YEAR, 2, DOWN));
    return new HeadroomFigures(
        wholeEuros(limit),
        limitCertainty(knownIncome, knownDeductions),
        wholeEuros(remaining),
        wholeEuros(remaining.multiply(INCOME_TAX_RATE)),
        wholeEuros(monthlyPayment),
        monthsLeft >= MONTHS_LEFT_FOR_A_RECURRING_PAYMENT ? RECURRING : SINGLE);
  }

  private static LimitCertainty limitCertainty(BigDecimal knownIncome, BigDecimal knownDeductions) {
    return limitFor(knownIncome, knownDeductions).compareTo(LIMIT_CAP) < 0 ? ESTIMATED : CERTAIN;
  }

  private static BigDecimal limitFor(BigDecimal income, BigDecimal deductions) {
    BigDecimal taxableIncome = income.subtract(YEARLY_TAX_FREE_INCOME).subtract(deductions);
    return income.multiply(LIMIT_SHARE_OF_GROSS_INCOME).min(LIMIT_CAP).min(taxableIncome).max(ZERO);
  }

  private static int wholeEuros(BigDecimal amount) {
    return amount.setScale(0, FLOOR).intValueExact();
  }
}
