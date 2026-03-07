package ee.tuleva.onboarding.investment.fees;

import java.time.LocalDate;
import java.time.Year;

public class FeeAccrualBuilder {

  public static int daysInYear(LocalDate date) {
    return Year.of(date.getYear()).length();
  }
}
