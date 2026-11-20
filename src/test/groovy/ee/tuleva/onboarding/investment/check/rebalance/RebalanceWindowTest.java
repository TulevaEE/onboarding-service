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
import java.util.Set;
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
  void anInLineNavSinceTheEffectiveDate_closesTheWindowWhateverTheDate() {
    var inLineSince = LocalDate.of(2026, 9, 2);

    assertThat(window(LocalDate.of(2026, 9, 3), inLineSince).state()).isEqualTo(OK);
    assertThat(window(WARNING_FROM, inLineSince).state()).isEqualTo(OK);
    assertThat(window(LocalDate.of(2027, 3, 1), inLineSince).state()).isEqualTo(OK);
    assertThat(window(LocalDate.of(2027, 3, 1), inLineSince).closedOn()).contains(inLineSince);
    assertThat(window(LocalDate.of(2027, 3, 1), null).closedOn()).isEmpty();
  }

  @Test
  void aRebalancingTradeClosesTheWindowEvenPastTheDeadlineAndWithoutAnInLineNav() {
    var tradeDate = LocalDate.of(2026, 12, 3);

    assertThat(window(LocalDate.of(2026, 12, 4), null, tradeDate).state()).isEqualTo(OK);
    assertThat(window(LocalDate.of(2026, 12, 4), null, tradeDate).closure())
        .contains(RebalanceClosure.byRebalancingTrades(tradeDate));
  }

  @Test
  void whicheverOfAnInLineNavAndARebalancingTradeComesFirstClosesTheWindowAndANavWinsATie() {
    var earlier = LocalDate.of(2026, 10, 5);
    var later = LocalDate.of(2026, 10, 9);
    var checkDate = LocalDate.of(2026, 10, 12);

    assertThat(window(checkDate, later, earlier).closure())
        .contains(RebalanceClosure.byRebalancingTrades(earlier));
    assertThat(window(checkDate, earlier, later).closure())
        .contains(RebalanceClosure.byAnInLineNav(earlier));
    assertThat(window(checkDate, earlier, earlier).closure())
        .contains(RebalanceClosure.byAnInLineNav(earlier));
  }

  @Test
  void countsDaysFromTheEffectiveDateAndUntilTheDeadline() {
    var window = window(LocalDate.of(2026, 10, 1), null);

    assertThat(window.daysSinceModelEffectiveDate()).isEqualTo(30);
    assertThat(window.daysUntilDeadline()).isEqualTo(61);
  }

  private static RebalanceWindow window(LocalDate checkDate, @Nullable LocalDate inLineSince) {
    return window(checkDate, inLineSince, null);
  }

  private static RebalanceWindow window(
      LocalDate checkDate, @Nullable LocalDate inLineSince, @Nullable LocalDate rebalancedOn) {
    return new RebalanceWindow(
        TUV100,
        EFFECTIVE,
        checkDate,
        new BigDecimal("0.05"),
        Set.of("WORLD", "NEW_WORLD"),
        null,
        List.of(),
        inLineSince,
        rebalancedOn,
        DEADLINE,
        WARNING_FROM);
  }
}
