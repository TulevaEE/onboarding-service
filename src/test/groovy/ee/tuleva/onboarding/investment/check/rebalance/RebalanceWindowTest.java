package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.FAIL;
import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.INFO;
import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.OK;
import static ee.tuleva.onboarding.investment.check.rebalance.RebalanceState.WARNING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RebalanceWindowTest {

  private static final LocalDate EFFECTIVE = LocalDate.of(2026, 9, 1);
  private static final LocalDate DEADLINE = LocalDate.of(2026, 12, 1);
  private static final LocalDate WARNING_FROM = LocalDate.of(2026, 11, 16);

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

  private static RebalanceWindow window(LocalDate checkDate, @Nullable RebalanceClosure closure) {
    return new RebalanceWindow(
        TUV100,
        EFFECTIVE,
        checkDate,
        new BigDecimal("0.05"),
        null,
        List.of(),
        closure,
        DEADLINE,
        WARNING_FROM);
  }
}
