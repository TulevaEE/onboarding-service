package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionCutoff.TALLINN;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class RedemptionPayoutDeadlineTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 4, 13);
  private static final LocalDate SATURDAY = LocalDate.of(2026, 4, 18);

  private final RedemptionPayoutDeadline deadline =
      new RedemptionPayoutDeadline(new PublicHolidays());

  @Test
  void anOrderBeforeTheCutOffIsThatDaysOrder() {
    assertThat(deadline.orderDay(at(MONDAY, 15, 59))).isEqualTo(MONDAY);
  }

  // The cut-off moves T itself, so the whole bound shifts with it.
  @Test
  void anOrderAtTheCutOffBelongsToTheNextWorkingDay() {
    assertThat(deadline.orderDay(at(MONDAY, 16, 0))).isEqualTo(MONDAY.plusDays(1));
  }

  @Test
  void anOrderOnANonWorkingDayBelongsToTheNextWorkingDay() {
    assertThat(deadline.orderDay(at(SATURDAY, 11, 0))).isEqualTo(LocalDate.of(2026, 4, 20));
  }

  // Warn at T+2 rather than on the bound: an alert on T+3 reports a breach already in progress.
  @Test
  void theWarningComesTwoWorkingDaysAfterTheOrderDayAndTheBoundOneDayLater() {
    var ordered = at(MONDAY, 10, 0);

    assertThat(deadline.warnFrom(ordered)).isEqualTo(LocalDate.of(2026, 4, 15));
    assertThat(deadline.bound(ordered)).isEqualTo(LocalDate.of(2026, 4, 16));
  }

  @Test
  void aWeekendBetweenTheOrderAndTheBoundDoesNotCountAgainstIt() {
    var orderedOnFriday = at(LocalDate.of(2026, 4, 17), 10, 0);

    assertThat(deadline.warnFrom(orderedOnFriday)).isEqualTo(LocalDate.of(2026, 4, 21));
    assertThat(deadline.bound(orderedOnFriday)).isEqualTo(LocalDate.of(2026, 4, 22));
  }

  private static Instant at(LocalDate date, int hour, int minute) {
    return ZonedDateTime.of(date, LocalTime.of(hour, minute), TALLINN).toInstant();
  }
}
