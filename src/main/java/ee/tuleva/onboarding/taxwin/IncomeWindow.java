package ee.tuleva.onboarding.taxwin;

import java.time.YearMonth;

public record IncomeWindow(YearMonth first, YearMonth last) {

  private static final int LONGEST_TAX_DATA_PERIOD_IN_MONTHS = 12;

  static IncomeWindow endingBefore(YearMonth month) {
    return new IncomeWindow(
        month.minusMonths(LONGEST_TAX_DATA_PERIOD_IN_MONTHS), month.minusMonths(1));
  }

  public boolean contains(YearMonth month) {
    return !month.isBefore(first) && !month.isAfter(last);
  }
}
