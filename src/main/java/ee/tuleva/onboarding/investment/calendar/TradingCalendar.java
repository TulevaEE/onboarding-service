package ee.tuleva.onboarding.investment.calendar;

import java.time.LocalDate;

public interface TradingCalendar {

  boolean isBusinessDay(LocalDate date);

  default LocalDate addBusinessDays(LocalDate date, int businessDays) {
    LocalDate result = date;
    for (int remaining = businessDays; remaining > 0; remaining--) {
      result = nextBusinessDay(result);
    }
    return result;
  }

  default LocalDate nextOrSameBusinessDay(LocalDate date) {
    LocalDate result = date;
    while (!isBusinessDay(result)) {
      result = result.plusDays(1);
    }
    return result;
  }

  default LocalDate subtractBusinessDays(LocalDate date, int businessDays) {
    LocalDate result = date;
    for (int remaining = businessDays; remaining > 0; remaining--) {
      result = previousBusinessDay(result);
    }
    return result;
  }

  private LocalDate nextBusinessDay(LocalDate date) {
    LocalDate result = date.plusDays(1);
    while (!isBusinessDay(result)) {
      result = result.plusDays(1);
    }
    return result;
  }

  private LocalDate previousBusinessDay(LocalDate date) {
    LocalDate result = date.minusDays(1);
    while (!isBusinessDay(result)) {
      result = result.minusDays(1);
    }
    return result;
  }
}
