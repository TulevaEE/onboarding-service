package ee.tuleva.onboarding.investment.check.rebalance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RebalanceClosureTest {

  private static final LocalDate EARLIER = LocalDate.of(2026, 10, 5);
  private static final LocalDate LATER = LocalDate.of(2026, 10, 9);

  @Test
  void whicheverOfAnInLineNavAndARebalancingTradeComesFirstClosesTheWindow() {
    assertThat(RebalanceClosure.whicheverFirst(Optional.of(LATER), Optional.of(EARLIER)))
        .contains(RebalanceClosure.byRebalancingTrades(EARLIER));
    assertThat(RebalanceClosure.whicheverFirst(Optional.of(EARLIER), Optional.of(LATER)))
        .contains(RebalanceClosure.byAnInLineNav(EARLIER));
  }

  @Test
  void onTheSameDayAnInLineNavWins() {
    assertThat(RebalanceClosure.whicheverFirst(Optional.of(EARLIER), Optional.of(EARLIER)))
        .contains(RebalanceClosure.byAnInLineNav(EARLIER));
  }

  @Test
  void eitherAloneClosesTheWindowAndNeitherLeavesItOpen() {
    assertThat(RebalanceClosure.whicheverFirst(Optional.empty(), Optional.of(LATER)))
        .contains(RebalanceClosure.byRebalancingTrades(LATER));
    assertThat(RebalanceClosure.whicheverFirst(Optional.of(LATER), Optional.empty()))
        .contains(RebalanceClosure.byAnInLineNav(LATER));
    assertThat(RebalanceClosure.whicheverFirst(Optional.empty(), Optional.empty())).isEmpty();
  }
}
