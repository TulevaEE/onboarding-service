package ee.tuleva.onboarding.savings.fund.pricecheck;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingDaysTest {

  private static final LocalDate MONDAY = LocalDate.parse("2026-11-23");
  private static final PublicHolidays PUBLIC_HOLIDAYS = new PublicHolidays();

  @Test
  void withoutAnEarlierCheckStartsFromThePreviousWorkingDayAndCoversTheWeekend() {
    var days = BookingDays.sinceLastChecked(Optional.empty(), MONDAY, PUBLIC_HOLIDAYS);

    assertThat(days).isEqualTo(new BookingDays(LocalDate.parse("2026-11-20"), MONDAY));
    assertThat(days.describe()).isEqualTo("2026-11-20–2026-11-22");
    assertThat(days.from()).isEqualTo(Instant.parse("2026-11-19T22:00:00Z"));
    assertThat(days.to()).isEqualTo(Instant.parse("2026-11-22T22:00:00Z"));
  }

  @Test
  void continuesFromTheDayAfterTheLastCheckedOne() {
    var days =
        BookingDays.sinceLastChecked(
            Optional.of(LocalDate.parse("2026-11-18")), MONDAY, PUBLIC_HOLIDAYS);

    assertThat(days).isEqualTo(new BookingDays(LocalDate.parse("2026-11-19"), MONDAY));
    assertThat(days.isEmpty()).isFalse();
  }

  @Test
  void isEmptyWhenEveryDayBeforeTheRunDayIsChecked() {
    var days =
        BookingDays.sinceLastChecked(
            Optional.of(LocalDate.parse("2026-11-22")), MONDAY, PUBLIC_HOLIDAYS);

    assertThat(days.isEmpty()).isTrue();
  }

  @Test
  void namesASingleBookingDayOnItsOwn() {
    assertThat(
            new BookingDays(LocalDate.parse("2026-11-25"), LocalDate.parse("2026-11-26"))
                .describe())
        .isEqualTo("2026-11-25");
  }
}
