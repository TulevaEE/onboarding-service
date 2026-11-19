package ee.tuleva.onboarding.taxwin;

import java.math.BigDecimal;

record YearlyIncome(int year, BigDecimal amount) {

  boolean isFor(int year) {
    return this.year == year;
  }
}
