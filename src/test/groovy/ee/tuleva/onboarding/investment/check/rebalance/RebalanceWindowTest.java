package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.AWAITING_EXECUTION;
import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.FAIL;
import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.INFO;
import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.OK;
import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.WARNING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RebalanceWindowTest {

  private static final LocalDate EFFECTIVE = LocalDate.of(2026, 9, 1);
  private static final LocalDate DEADLINE = LocalDate.of(2026, 12, 1);
  private static final LocalDate WARNING_FROM = LocalDate.of(2026, 11, 16);
  private static final EstonianCalendar CALENDAR = new EstonianCalendar(new PublicHolidays());

  @Test
  void beforeTheWarningPeriod_isInfo() {
    assertThat(window(EFFECTIVE, null).state()).isEqualTo(INFO);
    assertThat(window(LocalDate.of(2026, 11, 15), null).state()).isEqualTo(INFO);
  }

  @Test
  void fromTheFirstWarningDayUpToAndIncludingTheDeadline_isWarning() {
    assertThat(window(WARNING_FROM, null).state()).isEqualTo(WARNING);
    assertThat(window(DEADLINE, null).state()).isEqualTo(WARNING);
  }

  @Test
  void fromTheDayAfterTheDeadline_isFailUntilClosed() {
    assertThat(window(DEADLINE.plusDays(1), null).state()).isEqualTo(FAIL);
    assertThat(window(LocalDate.of(2027, 3, 1), null).state()).isEqualTo(FAIL);
  }

  @Test
  void pastTheDeadlineAnOrderSentByItAwaitsExecutionThroughItsOwnGrace_thenItFails() {
    var sentOnTheDeadline = sent(DEADLINE, LocalDate.of(2026, 12, 3));

    assertThat(window(DEADLINE.plusDays(1), null, sentOnTheDeadline).state())
        .isEqualTo(AWAITING_EXECUTION);
    assertThat(window(LocalDate.of(2026, 12, 4), null, sentOnTheDeadline).state())
        .isEqualTo(AWAITING_EXECUTION);
    assertThat(window(LocalDate.of(2026, 12, 7), null, sentOnTheDeadline).state()).isEqualTo(FAIL);
  }

  @Test
  void pastTheDeadlineAnOrderSentByItWithinItsGrace_awaitsExecutionWhileAnEarlierOneIsOverdue() {
    var overdue = sent(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 17));
    var sentOnTheDeadline = sent(DEADLINE, LocalDate.of(2026, 12, 3));

    var window = window(LocalDate.of(2026, 12, 2), null, overdue, sentOnTheDeadline);

    assertThat(window.state()).isEqualTo(AWAITING_EXECUTION);
    assertThat(window.ordersAwaitingConfirmation()).containsExactly(sentOnTheDeadline);
    assertThat(window.ordersOverdueForConfirmation()).containsExactly(overdue);
  }

  @Test
  void pastTheDeadlineAnOrderSentOnlyAfterIt_leavesItAFailEvenWithinTheOrdersGrace() {
    var sentAfterTheDeadline = sent(LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 4));

    var window = window(LocalDate.of(2026, 12, 3), null, sentAfterTheDeadline);

    assertThat(window.state()).isEqualTo(FAIL);
    assertThat(window.ordersAwaitingConfirmation()).containsExactly(sentAfterTheDeadline);
    assertThat(window.ordersOverdueForConfirmation()).isEmpty();
  }

  @Test
  void beforeTheDeadlineRebalanceOrdersAwaitingOrOverdue_changeNoState() {
    assertThat(
            window(
                    LocalDate.of(2026, 10, 1),
                    null,
                    sent(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 2)))
                .state())
        .isEqualTo(INFO);
    assertThat(window(WARNING_FROM, null, sent(WARNING_FROM, LocalDate.of(2026, 11, 18))).state())
        .isEqualTo(WARNING);
    assertThat(
            window(
                    LocalDate.of(2026, 11, 25),
                    null,
                    sent(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 17)))
                .state())
        .isEqualTo(WARNING);
  }

  @Test
  void aClosure_closesTheWindowWhateverTheDate() {
    var closure = RebalanceClosure.byAnInLineNav(LocalDate.of(2026, 9, 2));

    assertThat(window(LocalDate.of(2026, 9, 3), closure).state()).isEqualTo(OK);
    assertThat(window(WARNING_FROM, closure).state()).isEqualTo(OK);
    assertThat(window(LocalDate.of(2027, 3, 1), closure).state()).isEqualTo(OK);
    assertThat(window(LocalDate.of(2027, 3, 1), closure).closedOn())
        .contains(LocalDate.of(2026, 9, 2));
    assertThat(window(LocalDate.of(2027, 3, 1), null).closedOn()).isEmpty();
  }

  @Test
  void countsDaysUntilTheDeadline() {
    assertThat(window(LocalDate.of(2026, 10, 1), null).daysUntilDeadline()).isEqualTo(61);
    assertThat(window(DEADLINE, null).daysUntilDeadline()).isZero();
  }

  private static RebalanceWindow window(
      LocalDate checkDate, @Nullable RebalanceClosure closure, SentRebalanceOrder... sent) {
    return new RebalanceWindow(
        TUV100,
        EFFECTIVE,
        checkDate,
        new BigDecimal("0.05"),
        null,
        List.of(),
        closure,
        List.of(sent),
        DEADLINE,
        WARNING_FROM);
  }

  private static SentRebalanceOrder sent(LocalDate sentOn, LocalDate expectedSettlementDate) {
    return SentRebalanceOrder.of("IE000I9HGDZ3", sentOn, expectedSettlementDate, CALENDAR);
  }
}
