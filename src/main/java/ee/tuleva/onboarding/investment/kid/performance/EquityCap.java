package ee.tuleva.onboarding.investment.kid.performance;

import java.math.BigDecimal;
import java.time.LocalDate;

record EquityCap(String bondKey, BigDecimal equityWeight, LocalDate liftedOn) {

  boolean holdsOn(LocalDate date) {
    return !date.isAfter(liftedOn);
  }

  boolean heldInAYearFrom(int firstYear, LocalDate fundStart) {
    return firstYear <= liftedOn.getYear() && fundStart.isBefore(liftedOn);
  }
}
