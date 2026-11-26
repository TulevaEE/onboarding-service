package ee.tuleva.onboarding.savings.fund.pricecheck;

import static ee.tuleva.onboarding.savings.fund.pricecheck.BookingDays.FIRST_BOOKING_DAY_THE_PER_DEAL_CHECK_COVERS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingDaysTest {

  private static final LocalDate MONDAY = LocalDate.parse("2026-12-07");

  @Test
  void withoutAnEarlierCheckStartsFromTheFirstDayTheCheckCoversSoAFailedFirstRunIsRetried() {
    var days = BookingDays.sinceLastChecked(Optional.empty(), MONDAY);

    assertThat(days)
        .isEqualTo(new BookingDays(FIRST_BOOKING_DAY_THE_PER_DEAL_CHECK_COVERS, MONDAY));
    assertThat(days.describe()).isEqualTo("2026-11-26–2026-12-06");
  }

  @Test
  void continuesFromTheDayAfterTheLastCheckedOneAcrossTheWeekend() {
    var days = BookingDays.sinceLastChecked(Optional.of(LocalDate.parse("2026-12-03")), MONDAY);

    assertThat(days).isEqualTo(new BookingDays(LocalDate.parse("2026-12-04"), MONDAY));
    assertThat(days.describe()).isEqualTo("2026-12-04–2026-12-06");
    assertThat(days.from()).isEqualTo(Instant.parse("2026-12-03T22:00:00Z"));
    assertThat(days.to()).isEqualTo(Instant.parse("2026-12-06T22:00:00Z"));
    assertThat(days.isEmpty()).isFalse();
  }

  @Test
  void isEmptyWhenEveryDayBeforeTheRunDayIsChecked() {
    var days = BookingDays.sinceLastChecked(Optional.of(LocalDate.parse("2026-12-06")), MONDAY);

    assertThat(days.isEmpty()).isTrue();
  }

  @Test
  void isEmptyBeforeTheFirstDayTheCheckCovers() {
    var days =
        BookingDays.sinceLastChecked(Optional.empty(), FIRST_BOOKING_DAY_THE_PER_DEAL_CHECK_COVERS);

    assertThat(days.isEmpty()).isTrue();
  }

  @Test
  void namesASingleBookingDayOnItsOwn() {
    assertThat(
            new BookingDays(LocalDate.parse("2026-12-02"), LocalDate.parse("2026-12-03"))
                .describe())
        .isEqualTo("2026-12-02");
  }
}
