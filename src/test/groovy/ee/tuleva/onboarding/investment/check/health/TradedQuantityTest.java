package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.PendingTradeChangesTest.trade;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TradedQuantityTest {

  @Test
  void netsBuysAndSellsOfTheSameIsin() {
    var traded =
        TradedQuantity.byIsin(
            List.of(
                trade(TUK75, "DLA1", "IE00A", BUY, "100", "1000"),
                trade(TUK75, "DLA2", "IE00A", SELL, "30", "300"),
                trade(TUK75, "DLA3", "IE00B", SELL, "5", "50")));

    assertThat(traded)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "IE00A", new TradedQuantity(new BigDecimal("100"), new BigDecimal("30")),
                "IE00B", new TradedQuantity(BigDecimal.ZERO, new BigDecimal("5"))));
    assertThat(traded.get("IE00A").netChange()).isEqualByComparingTo("70");
  }

  @Test
  void hasNothingForNoTrades() {
    assertThat(TradedQuantity.byIsin(List.of())).isEmpty();
  }
}
