package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.transaction.LimitStatus.*;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.*;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.investment.transaction.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TradeCalculationEngineTest {

  private final TradeCalculationEngine engine = new TradeCalculationEngine();

  @Test
  void calculate_surfacesNetInvestable() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("500000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(new BigDecimal("30000"))
            .receivables(new BigDecimal("20000"))
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    assertThat(result.netInvestable()).isEqualByComparingTo(new BigDecimal("940000"));
  }

  @Test
  void calculate_withTrades_hasNoNoTradeReason() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("500000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    assertThat(result.noTradeReason()).isNull();
  }

  @Test
  void buy_withANegativePositionMarketValue_failsLoudly() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("-500000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    assertThatThrownBy(() -> engine.calculate(input, BUY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void buy_withANegativeGrossPortfolioValue_failsLoudly() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("500000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("-1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    assertThatThrownBy(() -> engine.calculate(input, BUY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // A rebalance needs something to rebalance against; with no positions there is nothing to warn
  // about either.

  @Test
  void buy_withANegativeModelWeight_failsLoudly() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("500000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("-1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    assertThatThrownBy(() -> engine.calculate(input, BUY))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
