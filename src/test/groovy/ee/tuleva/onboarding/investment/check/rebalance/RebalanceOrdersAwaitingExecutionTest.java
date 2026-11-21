package ee.tuleva.onboarding.investment.check.rebalance;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.investment.check.rebalance.RebalanceOrdersAwaitingExecution.SentOrder;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RebalanceOrdersAwaitingExecutionTest {

  private static final LocalDate DEADLINE = LocalDate.of(2026, 12, 1);
  private static final EstonianCalendar CALENDAR = new EstonianCalendar(new PublicHolidays());

  @Test
  void ordersSentByTheDeadlineAreAwaitedThroughTheWorkingDayAfterTheLatestSettlementDate() {
    var orders =
        new RebalanceOrdersAwaitingExecution(
            List.of(
                new SentOrder(DEADLINE, LocalDate.of(2026, 12, 3)),
                new SentOrder(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 4))));

    assertThat(orders.awaitedThrough(DEADLINE, CALENDAR)).contains(LocalDate.of(2026, 12, 7));
    assertThat(orders.firstSentOn()).contains(LocalDate.of(2026, 11, 30));
  }

  @Test
  void anOrderSettlingOnAnEstonianOnlyHoliday_isAwaitedThroughTheSecondEstonianWorkingDayAfter() {
    var orders =
        new RebalanceOrdersAwaitingExecution(
            List.of(new SentOrder(LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 23))));

    assertThat(orders.awaitedThrough(LocalDate.of(2026, 6, 30), CALENDAR))
        .contains(LocalDate.of(2026, 6, 26));
  }

  @Test
  void anOrderWithoutAnExpectedSettlementDate_isTakenToSettleTwoWorkingDaysAfterTheDeadline() {
    var orders =
        new RebalanceOrdersAwaitingExecution(
            List.of(
                new SentOrder(DEADLINE, null), new SentOrder(DEADLINE, LocalDate.of(2026, 12, 2))));

    assertThat(orders.awaitedThrough(DEADLINE, CALENDAR)).contains(LocalDate.of(2026, 12, 4));
  }

  @Test
  void ordersSentOnlyAfterTheDeadlineOrNoOrdersAtAll_areNotAwaited() {
    var sentAfter =
        new RebalanceOrdersAwaitingExecution(
            List.of(new SentOrder(DEADLINE.plusDays(1), LocalDate.of(2026, 12, 4))));
    var none = new RebalanceOrdersAwaitingExecution(List.of());

    assertThat(sentAfter.awaitedThrough(DEADLINE, CALENDAR)).isEmpty();
    assertThat(sentAfter.firstSentOn()).contains(DEADLINE.plusDays(1));
    assertThat(none.awaitedThrough(DEADLINE, CALENDAR)).isEmpty();
    assertThat(none.firstSentOn()).isEmpty();
  }
}
