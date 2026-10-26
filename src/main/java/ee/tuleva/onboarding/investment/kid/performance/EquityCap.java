package ee.tuleva.onboarding.investment.kid.performance;

import static java.math.BigDecimal.ONE;

import java.math.BigDecimal;
import java.time.LocalDate;

record EquityCap(String bondKey, BigDecimal equityWeight, LocalDate liftedOn) {

  EquityCap {
    if (bondKey == null || equityWeight == null || liftedOn == null) {
      throw new IllegalStateException(
          "Equity cap incomplete: bondKey=%s, equityWeight=%s, liftedOn=%s"
              .formatted(bondKey, equityWeight, liftedOn));
    }
    if (equityWeight.signum() <= 0 || equityWeight.compareTo(ONE) >= 0) {
      throw new IllegalStateException(
          "Equity weight not a fraction: bondKey=%s, equityWeight=%s"
              .formatted(bondKey, equityWeight));
    }
  }

  boolean holdsOn(LocalDate date) {
    return !date.isAfter(liftedOn);
  }

  boolean heldInAYearFrom(int firstYear, LocalDate fundStart) {
    return firstYear <= liftedOn.getYear() && fundStart.isBefore(liftedOn);
  }
}
