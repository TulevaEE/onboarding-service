package ee.tuleva.onboarding.investment.kid;

import static java.time.Month.JANUARY;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

final class KidCalendar {

  static final YearMonth ART_8_3_SCENARIO_HISTORY_STARTS_WITH_THE_MONTH =
      YearMonth.of(2026, JANUARY);

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  private KidCalendar() {}

  static LocalDate today(Clock clock) {
    return LocalDate.now(clock.withZone(ESTONIAN_ZONE));
  }

  static YearMonth lastMonthEnded(LocalDate today) {
    return YearMonth.from(today).minusMonths(1);
  }
}
