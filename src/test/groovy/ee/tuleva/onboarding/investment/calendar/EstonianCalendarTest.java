package ee.tuleva.onboarding.investment.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EstonianCalendarTest {

  private final EstonianCalendar calendar = new EstonianCalendar(new PublicHolidays());

  @Test
  void regularWeekdayIsBusinessDay() {
    assertThat(calendar.isBusinessDay(LocalDate.of(2026, 6, 11))).isTrue();
  }

  @Test
  void weekendIsNotBusinessDay() {
    assertThat(calendar.isBusinessDay(LocalDate.of(2026, 6, 13))).isFalse();
    assertThat(calendar.isBusinessDay(LocalDate.of(2026, 6, 14))).isFalse();
  }

  @Test
  void estonianPublicHolidaysAreNotBusinessDays() {
    assertThat(calendar.isBusinessDay(LocalDate.of(2026, 2, 24))).isFalse();
    assertThat(calendar.isBusinessDay(LocalDate.of(2026, 6, 23))).isFalse();
    assertThat(calendar.isBusinessDay(LocalDate.of(2026, 6, 24))).isFalse();
    assertThat(calendar.isBusinessDay(LocalDate.of(2026, 8, 20))).isFalse();
    assertThat(calendar.isBusinessDay(LocalDate.of(2026, 12, 24))).isFalse();
  }

  @Test
  void nextOrSameBusinessDayKeepsBusinessDay() {
    assertThat(calendar.nextOrSameBusinessDay(LocalDate.of(2026, 9, 1)))
        .isEqualTo(LocalDate.of(2026, 9, 1));
  }

  @Test
  void nextOrSameBusinessDayRollsForwardOverHolidayAndWeekend() {
    assertThat(calendar.nextOrSameBusinessDay(LocalDate.of(2027, 1, 1)))
        .isEqualTo(LocalDate.of(2027, 1, 4));
  }

  @Test
  void subtractBusinessDaysSkipsEstonianHoliday() {
    assertThat(calendar.subtractBusinessDays(LocalDate.of(2026, 8, 21), 1))
        .isEqualTo(LocalDate.of(2026, 8, 19));
  }
}
