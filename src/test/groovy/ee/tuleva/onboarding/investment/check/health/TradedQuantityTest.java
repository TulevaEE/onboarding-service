package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.OpenTradeChangesTest.trade;
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
  void sumsBoughtAndSoldQuantitiesPerIsin() {
    var traded =
        TradedQuantity.byIsin(
            List.of(
                trade(TUK75, "DLA1", "IE00BFG1TM61", BUY, "100", "1000.00"),
                trade(TUK75, "DLA2", "IE00BFG1TM61", BUY, "50.5", "505.00"),
                trade(TUK75, "DLA3", "IE00BFG1TM61", SELL, "20", "200.00"),
                trade(TUK75, "DLA4", "IE00BKPTWY98", SELL, "7", "70.00")));

    assertThat(traded)
        .isEqualTo(
            Map.of(
                "IE00BFG1TM61",
                new TradedQuantity(new BigDecimal("150.5"), new BigDecimal("20")),
                "IE00BKPTWY98",
                new TradedQuantity(BigDecimal.ZERO, new BigDecimal("7"))));
  }
}
