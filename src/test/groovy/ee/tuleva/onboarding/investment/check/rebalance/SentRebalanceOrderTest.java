package ee.tuleva.onboarding.investment.check.rebalance;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SentRebalanceOrderTest {

  private static final String NEW_WORLD = "IE000I9HGDZ3";
  private static final EstonianCalendar CALENDAR = new EstonianCalendar(new PublicHolidays());

  @Test
  void anOrderIsAwaitedThroughTheWorkingDayAfterItsSettlementDate_andOverdueFromTheNextDay() {
    var order =
        SentRebalanceOrder.of(
            NEW_WORLD, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), CALENDAR);

    assertThat(order)
        .isEqualTo(
            new SentRebalanceOrder(
                NEW_WORLD, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 4)));
    assertThat(order.confirmationOverdueOn(LocalDate.of(2026, 12, 4))).isFalse();
    assertThat(order.confirmationOverdueOn(LocalDate.of(2026, 12, 5))).isTrue();
  }

  @Test
  void anOrderSettlingOnAnEstonianOnlyHoliday_isAwaitedThroughTheSecondEstonianWorkingDayAfter() {
    var order =
        SentRebalanceOrder.of(
            NEW_WORLD, LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 23), CALENDAR);

    assertThat(order.awaitedThrough()).isEqualTo(LocalDate.of(2026, 6, 26));
  }

  @Test
  void anOrderWithoutAnExpectedSettlementDate_isAwaitedAsLongAsAFundOrderSettlingT4() {
    var order = SentRebalanceOrder.of(NEW_WORLD, LocalDate.of(2026, 11, 27), null, CALENDAR);

    assertThat(order.confirmationOverdueOn(LocalDate.of(2026, 12, 3))).isFalse();
    assertThat(order.confirmationOverdueOn(LocalDate.of(2026, 12, 4))).isFalse();
    assertThat(order.confirmationOverdueOn(LocalDate.of(2026, 12, 7))).isTrue();
  }
}
