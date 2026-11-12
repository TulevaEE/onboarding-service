package ee.tuleva.onboarding.comparisons.fundvalue;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EquityCap(String bondKey, BigDecimal equityWeight, LocalDate liftedOn) {

  public boolean holdsOn(LocalDate date) {
    return !date.isAfter(liftedOn);
  }

  public boolean heldInAYearFrom(int firstYear, LocalDate fundStart) {
    return firstYear <= liftedOn.getYear() && fundStart.isBefore(liftedOn);
  }
}
