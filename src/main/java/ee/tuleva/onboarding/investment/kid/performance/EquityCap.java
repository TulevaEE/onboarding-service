package ee.tuleva.onboarding.investment.kid.performance;

import java.math.BigDecimal;
import java.time.LocalDate;

record EquityCap(String bondKey, BigDecimal equityWeight, LocalDate liftedOn) {

  EquityCap {
    if (bondKey == null || equityWeight == null || liftedOn == null) {
      throw new IllegalStateException(
          "Equity cap incomplete: bondKey=%s, equityWeight=%s, liftedOn=%s"
              .formatted(bondKey, equityWeight, liftedOn));
    }
  }

  boolean holdsOn(LocalDate date) {
    return !date.isAfter(liftedOn);
  }
}
