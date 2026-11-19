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

  private static final LocalDate EFFECTIVE = LocalDate.of(2026, 10, 1);
  private static final RebalanceDeadlines DEADLINES =
      new RebalanceDeadlines(
          LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 4));
  private static final Set<String> NO_TRADES = Set.of();
  private static final Set<String> A_TRADE = Set.of("NEW_WORLD");

  @Test
  void inTheFirstMonthWithoutAnInLineNav_isInfoWhetherOrNotThereAreTrades() {
    assertThat(window(LocalDate.of(2026, 10, 31), NO_TRADES, null).state()).isEqualTo(INFO);
    assertThat(window(LocalDate.of(2026, 10, 31), A_TRADE, null).state()).isEqualTo(INFO);
  }

  @Test
  void fromTheTradesDueDateWithoutATrade_isWarningAndATradeClearsItBackToInfo() {
    assertThat(window(LocalDate.of(2026, 11, 1), NO_TRADES, null).state()).isEqualTo(WARNING);
    assertThat(window(LocalDate.of(2026, 11, 30), NO_TRADES, null).state()).isEqualTo(WARNING);
    assertThat(window(LocalDate.of(2026, 11, 1), A_TRADE, null).state()).isEqualTo(INFO);
  }

  @Test
  void fromTheInLineDueDateWithoutAnInLineNav_isFailWhetherOrNotThereAreTrades() {
    assertThat(window(LocalDate.of(2026, 12, 1), NO_TRADES, null).state()).isEqualTo(FAIL);
    assertThat(window(LocalDate.of(2027, 3, 1), A_TRADE, null).state()).isEqualTo(FAIL);
  }

  @Test
  void anInLineNavSinceTheEffectiveDate_closesTheWindowAtAnyTimeAndWithoutTrades() {
    var inLineSince = LocalDate.of(2026, 10, 2);

    assertThat(window(LocalDate.of(2026, 10, 5), NO_TRADES, inLineSince).state()).isEqualTo(OK);
    assertThat(window(LocalDate.of(2027, 3, 1), NO_TRADES, inLineSince).state()).isEqualTo(OK);
    assertThat(window(LocalDate.of(2026, 10, 5), NO_TRADES, inLineSince).inLine()).isTrue();
    assertThat(window(LocalDate.of(2026, 10, 5), NO_TRADES, null).inLine()).isFalse();
  }

  @Test
  void theWindowIsClosedOnTheFirstInLineNavWhicheverNavIsTheLatest() {
    var closing = LocalDate.of(2026, 10, 30);

    assertThat(window(closing, closing).closedOn()).contains(closing);
    assertThat(window(closing, LocalDate.of(2026, 11, 2)).closedOn()).contains(closing);
    assertThat(window(null, closing).closedOn()).isEmpty();
  }

  @Test
  void countsDaysFromTheEffectiveDateAndUntilTheInLineDueDate() {
    var window = window(LocalDate.of(2026, 10, 5), NO_TRADES, null);

    assertThat(window.daysSinceModelEffectiveDate()).isEqualTo(4);
    assertThat(window.daysUntilInLineDue()).isEqualTo(57);
    assertThat(window.tradesDone()).isFalse();
    assertThat(window(LocalDate.of(2026, 10, 5), A_TRADE, null).tradesDone()).isTrue();
  }

  private static RebalanceWindow window(
      LocalDate checkDate, Set<String> tradedIsins, @Nullable LocalDate inLineSince) {
    return new RebalanceWindow(
        TUV100,
        EFFECTIVE,
        checkDate,
        new BigDecimal("0.05"),
        Set.of("WORLD", "NEW_WORLD"),
        tradedIsins,
        null,
        List.of(),
        inLineSince,
        DEADLINES);
  }

  private static RebalanceWindow window(@Nullable LocalDate inLineSince, LocalDate latestNavDate) {
    return new RebalanceWindow(
        TUV100,
        EFFECTIVE,
        latestNavDate.plusDays(3),
        new BigDecimal("0.05"),
        Set.of("WORLD", "NEW_WORLD"),
        NO_TRADES,
        latestNavDate,
        List.of(),
        inLineSince,
        DEADLINES);
  }
}
