package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.transaction.LimitStatus.*;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.*;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TradeCalculationEngineTest {

  private final TradeCalculationEngine engine = new TradeCalculationEngine();

  @Test
  void buy_allocatesFreeCashToUnderweightPositions() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("300000")),
                    new PositionSnapshot("IE00B", new BigDecimal("200000")),
                    new PositionSnapshot("IE00C", new BigDecimal("100000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.40")),
                    new ModelWeight("IE00B", new BigDecimal("0.35")),
                    new ModelWeight("IE00C", new BigDecimal("0.25"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                        new PositionLimitSnapshot(new BigDecimal("0.50"), new BigDecimal("0.60")),
                    "IE00B",
                        new PositionLimitSnapshot(new BigDecimal("0.50"), new BigDecimal("0.60")),
                    "IE00C",
                        new PositionLimitSnapshot(new BigDecimal("0.50"), new BigDecimal("0.60"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    assertThat(result.fund()).isEqualTo(TUV100);
    assertThat(result.mode()).isEqualTo(BUY);
    assertThat(result.trades()).hasSize(3);

    BigDecimal totalTradeAmount =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalTradeAmount)
        .isCloseTo(
            new BigDecimal("100000"), org.assertj.core.data.Offset.offset(new BigDecimal("1")));

    result
        .trades()
        .forEach(
            trade -> {
              assertThat(trade.tradeAmount()).isGreaterThanOrEqualTo(ZERO);
              assertThat(trade.limitStatus()).isEqualTo(OK);
            });
  }

  @Test
  void buy_withZeroFreeCash_returnsZeroTrades() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("500000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    assertThat(result.trades()).hasSize(1);
    assertThat(result.trades().getFirst().tradeAmount()).isEqualByComparingTo(ZERO);
  }

  @Test
  void buy_removesPositionsBelowThreshold() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("400000")),
                    new PositionSnapshot("IE00B", new BigDecimal("549000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("10000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var nonZeroTrades =
        result.trades().stream().filter(t -> t.tradeAmount().compareTo(ZERO) > 0).toList();
    nonZeroTrades.forEach(
        trade -> assertThat(trade.tradeAmount()).isGreaterThanOrEqualTo(new BigDecimal("5000")));
  }

  @Test
  void buy_clipsAtHardLimit() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("580000")),
                    new PositionSnapshot("IE00B", new BigDecimal("20000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.80")),
                    new ModelWeight("IE00B", new BigDecimal("0.20"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("200000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                        new PositionLimitSnapshot(new BigDecimal("0.55"), new BigDecimal("0.60")),
                    "IE00B",
                        new PositionLimitSnapshot(new BigDecimal("0.50"), new BigDecimal("0.60"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    assertThat(tradeA.projectedWeight()).isLessThanOrEqualTo(new BigDecimal("0.6001"));
    assertThat(tradeA.limitStatus()).isEqualTo(HARD_LIMIT_EXCEEDED);
  }

  @Test
  void buy_flagsSoftLimitExceeded() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("400000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("200000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                    new PositionLimitSnapshot(new BigDecimal("0.50"), new BigDecimal("0.70"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var trade = result.trades().getFirst();
    assertThat(trade.limitStatus()).isEqualTo(SOFT_LIMIT_EXCEEDED);
  }

  @Test
  void sell_sellsOverweightPositions() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("600000")),
                    new PositionSnapshot("IE00B", new BigDecimal("300000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                        new PositionLimitSnapshot(new BigDecimal("0.55"), new BigDecimal("0.65")),
                    "IE00B",
                        new PositionLimitSnapshot(new BigDecimal("0.50"), new BigDecimal("0.60"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    assertThat(result.trades()).hasSize(2);

    BigDecimal totalSold =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalSold).isEqualByComparingTo(new BigDecimal("-100000"));
  }

  @Test
  void sell_withPositiveFreeCash_returnsZeroTrades() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("500000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("50000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    result.trades().forEach(trade -> assertThat(trade.tradeAmount()).isEqualByComparingTo(ZERO));
  }

  @Test
  void sell_prefersPositionsOverSoftLimit() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("600000")),
                    new PositionSnapshot("IE00B", new BigDecimal("200000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-50000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                        new PositionLimitSnapshot(new BigDecimal("0.55"), new BigDecimal("0.65")),
                    "IE00B",
                        new PositionLimitSnapshot(new BigDecimal("0.50"), new BigDecimal("0.60"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();

    assertThat(tradeA.tradeAmount()).isLessThan(ZERO);
    assertThat(tradeB.tradeAmount()).isEqualByComparingTo(ZERO);
  }

  @Test
  void sellFast_sellsFastTaggedInstrumentsFirst() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00FAST", new BigDecimal("200000")),
                    new PositionSnapshot("IE00SLOW", new BigDecimal("300000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00FAST", new BigDecimal("0.40")),
                    new ModelWeight("IE00SLOW", new BigDecimal("0.60"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of("IE00FAST"))
            .build();

    var result = engine.calculate(input, SELL_FAST);

    var fastTrade =
        result.trades().stream().filter(t -> t.isin().equals("IE00FAST")).findFirst().orElseThrow();
    assertThat(fastTrade.tradeAmount()).isLessThan(ZERO);

    BigDecimal totalSold =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalSold).isEqualByComparingTo(new BigDecimal("-100000"));
  }

  @Test
  void sellFast_overflowsToSlowInstruments() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00FAST", new BigDecimal("50000")),
                    new PositionSnapshot("IE00SLOW", new BigDecimal("300000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00FAST", new BigDecimal("0.10")),
                    new ModelWeight("IE00SLOW", new BigDecimal("0.90"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of("IE00FAST"))
            .build();

    var result = engine.calculate(input, SELL_FAST);

    var fastTrade =
        result.trades().stream().filter(t -> t.isin().equals("IE00FAST")).findFirst().orElseThrow();
    assertThat(fastTrade.tradeAmount()).isEqualByComparingTo(new BigDecimal("-50000"));

    var slowTrade =
        result.trades().stream().filter(t -> t.isin().equals("IE00SLOW")).findFirst().orElseThrow();
    assertThat(slowTrade.tradeAmount()).isEqualByComparingTo(new BigDecimal("-50000"));
  }

  @Test
  void sellFast_withPositiveFreeCash_returnsZeroTrades() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("200000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("50000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of("IE00A"))
            .build();

    var result = engine.calculate(input, SELL_FAST);

    result.trades().forEach(trade -> assertThat(trade.tradeAmount()).isEqualByComparingTo(ZERO));
  }

  @Test
  void rebalance_movesPositionsTowardModelWeights() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("600000")),
                    new PositionSnapshot("IE00B", new BigDecimal("200000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();

    assertThat(tradeA.tradeAmount()).isLessThan(ZERO);
    assertThat(tradeB.tradeAmount()).isGreaterThan(ZERO);
  }

  @Test
  void rebalance_normalizesWeights() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("500000")),
                    new PositionSnapshot("IE00B", new BigDecimal("400000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.60")),
                    new ModelWeight("IE00B", new BigDecimal("0.40"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();

    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(new BigDecimal("70000"));
    assertThat(tradeB.tradeAmount()).isEqualByComparingTo(new BigDecimal("-20000"));
  }

  @Test
  void emptyPositions_returnsEmptyTrades() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of())
            .modelWeights(List.of())
            .grossPortfolioValue(ZERO)
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    assertThat(result.trades()).isEmpty();
  }

  @Test
  void singlePosition_getsFullAllocation() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("400000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    assertThat(result.trades()).hasSize(1);
    assertThat(result.trades().getFirst().tradeAmount())
        .isEqualByComparingTo(new BigDecimal("100000"));
  }

  @Test
  void buy_positionWithNoModelWeight_getsZeroAllocation() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("400000")),
                    new PositionSnapshot("IE00UNKNOWN", new BigDecimal("100000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var unknownTrade =
        result.trades().stream()
            .filter(t -> t.isin().equals("IE00UNKNOWN"))
            .findFirst()
            .orElseThrow();
    assertThat(unknownTrade.tradeAmount()).isEqualByComparingTo(ZERO);
  }

  @Test
  void sell_fallbackToStandardWhenAllWithinSoftLimit() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("500000")),
                    new PositionSnapshot("IE00B", new BigDecimal("400000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-50000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                        new PositionLimitSnapshot(new BigDecimal("0.60"), new BigDecimal("0.70")),
                    "IE00B",
                        new PositionLimitSnapshot(new BigDecimal("0.60"), new BigDecimal("0.70"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    BigDecimal totalSold =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalSold).isEqualByComparingTo(new BigDecimal("-50000"));

    var nonZeroTrades =
        result.trades().stream().filter(t -> t.tradeAmount().compareTo(ZERO) != 0).toList();
    assertThat(nonZeroTrades).isNotEmpty();
  }
}
