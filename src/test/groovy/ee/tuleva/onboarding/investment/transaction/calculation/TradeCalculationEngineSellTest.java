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

class TradeCalculationEngineSellTest {

  private final TradeCalculationEngine engine = new TradeCalculationEngine();

  @Test
  void sell_withoutCashShortfall_recordsNoCashShortfallReason() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("500000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    assertThat(result.noTradeReason())
        .isEqualTo("No trades: mode=SELL, reason=noCashShortfall, freeCash=0");
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
  void sell_targetsOnlyOverweightPositionsAndLeavesOnTargetPositionUntouched() {
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
            .grossPortfolioValue(new BigDecimal("900000"))
            .cashBuffer(new BigDecimal("100000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();

    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(new BigDecimal("-100000"));
    assertThat(tradeB.tradeAmount()).isEqualByComparingTo(ZERO);
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
  void sell_capsOverSoftReliefAtModelAndSpillsExcessToOtherOverweightFunds() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("550000")),
                    new PositionSnapshot("IE00B", new BigDecimal("350000")),
                    new PositionSnapshot("IE00C", new BigDecimal("100000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.40")),
                    new ModelWeight("IE00B", new BigDecimal("0.30")),
                    new ModelWeight("IE00C", new BigDecimal("0.30"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-200000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                        new PositionLimitSnapshot(new BigDecimal("0.50"), new BigDecimal("0.60")),
                    "IE00B",
                        new PositionLimitSnapshot(new BigDecimal("0.40"), new BigDecimal("0.50"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    assertThat(result.trades())
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            List.of(
                new TradeCalculation(
                    "IE00A", new BigDecimal("-150000"), new BigDecimal("0.400000"), OK),
                new TradeCalculation(
                    "IE00B", new BigDecimal("-50000"), new BigDecimal("0.300000"), OK),
                new TradeCalculation("IE00C", ZERO, new BigDecimal("0.100000"), OK)));
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
  void sellFast_sellsMostOverweightInstrumentsFirst() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("500000")),
                    new PositionSnapshot("IE00B", new BigDecimal("400000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.45")),
                    new ModelWeight("IE00B", new BigDecimal("0.45"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-25000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of("IE00A", "IE00B"))
            .build();

    var result = engine.calculate(input, SELL_FAST);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();

    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(new BigDecimal("-25000.00"));
    assertThat(tradeB.tradeAmount()).isEqualByComparingTo(ZERO);
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
  void sell_stopsWhenNeedFullyCoveredByExactMarketValueCap() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00OVER", new BigDecimal("40000")),
                    new PositionSnapshot("IE00ATTARGET", new BigDecimal("100000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00OVER", ZERO),
                    new ModelWeight("IE00ATTARGET", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-40000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    var overTrade =
        result.trades().stream().filter(t -> t.isin().equals("IE00OVER")).findFirst().orElseThrow();
    var atTargetTrade =
        result.trades().stream()
            .filter(t -> t.isin().equals("IE00ATTARGET"))
            .findFirst()
            .orElseThrow();

    assertThat(overTrade.tradeAmount()).isEqualByComparingTo(new BigDecimal("-40000"));
    assertThat(atTargetTrade.tradeAmount()).isEqualByComparingTo(ZERO);
  }

  @Test
  void sellFast_leavesShortfallWhenNoSlowInstrumentsRemain() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00FAST", new BigDecimal("50000"))))
            .modelWeights(List.of(new ModelWeight("IE00FAST", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-80000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of("IE00FAST"))
            .build();

    var result = engine.calculate(input, SELL_FAST);

    var fastTrade =
        result.trades().stream().filter(t -> t.isin().equals("IE00FAST")).findFirst().orElseThrow();
    assertThat(fastTrade.tradeAmount()).isEqualByComparingTo(new BigDecimal("-50000"));

    BigDecimal totalSold =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalSold).isEqualByComparingTo(new BigDecimal("-50000"));
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

  @Test
  void sellFast_redistributesMarketValueCapShortfallWithinFastBucket() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00SMALL", new BigDecimal("10000")),
                    new PositionSnapshot("IE00BIG", new BigDecimal("100000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00SMALL", ZERO),
                    new ModelWeight("IE00BIG", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-15000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of("IE00SMALL", "IE00BIG"))
            .build();

    var result = engine.calculate(input, SELL_FAST);

    var smallTrade =
        result.trades().stream()
            .filter(t -> t.isin().equals("IE00SMALL"))
            .findFirst()
            .orElseThrow();
    assertThat(smallTrade.tradeAmount()).isGreaterThanOrEqualTo(new BigDecimal("-10000"));

    BigDecimal totalSold =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalSold).isEqualByComparingTo(new BigDecimal("-15000"));
  }

  @Test
  void
      sell_respectsMinTradeFloorAndLeavesSubThresholdResidualToReserveRatherThanDumpingOnAtModelPosition() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00OVER", new BigDecimal("40000")),
                    new PositionSnapshot("IE00ATTARGET", new BigDecimal("100000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00OVER", ZERO),
                    new ModelWeight("IE00ATTARGET", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-44000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    assertThat(result.trades())
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            List.of(
                new TradeCalculation(
                    "IE00OVER", new BigDecimal("-40000"), new BigDecimal("0.000000"), OK),
                new TradeCalculation("IE00ATTARGET", ZERO, new BigDecimal("0.100000"), OK)));
  }

  @Test
  void sell_failsWhenTotalSellNeedExceedsSellableMarketValue() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("40000")),
                    new PositionSnapshot("IE00B", new BigDecimal("30000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-90000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    assertThatThrownBy(() -> engine.calculate(input, SELL))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Insufficient liquidity");
  }

  @Test
  void sellFast_weightsFastBucketSellsByOverweightBeforeTouchingSlowBucket() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00F1", new BigDecimal("250000")),
                    new PositionSnapshot("IE00F2", new BigDecimal("210000")),
                    new PositionSnapshot("IE00SLOW", new BigDecimal("240000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00F1", new BigDecimal("0.30")),
                    new ModelWeight("IE00F2", new BigDecimal("0.30")),
                    new ModelWeight("IE00SLOW", new BigDecimal("0.40"))))
            .grossPortfolioValue(new BigDecimal("640000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-60000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of("IE00F1", "IE00F2"))
            .build();

    var result = engine.calculate(input, SELL_FAST);

    assertThat(result.trades())
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            List.of(
                new TradeCalculation(
                    "IE00F1", new BigDecimal("-45789.47"), new BigDecimal("0.319079"), OK),
                new TradeCalculation(
                    "IE00F2", new BigDecimal("-14210.53"), new BigDecimal("0.305921"), OK),
                new TradeCalculation("IE00SLOW", ZERO, new BigDecimal("0.375000"), OK)));
  }

  @Test
  void sellFast_liquidatesFastBucketFullyBeforeSpillingRemainderToSlowBucket() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00F1", new BigDecimal("250000")),
                    new PositionSnapshot("IE00F2", new BigDecimal("210000")),
                    new PositionSnapshot("IE00SLOW", new BigDecimal("240000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00F1", new BigDecimal("0.30")),
                    new ModelWeight("IE00F2", new BigDecimal("0.30")),
                    new ModelWeight("IE00SLOW", new BigDecimal("0.40"))))
            .grossPortfolioValue(new BigDecimal("200000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-500000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of("IE00F1", "IE00F2"))
            .build();

    var result = engine.calculate(input, SELL_FAST);

    assertThat(result.trades())
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            List.of(
                new TradeCalculation(
                    "IE00F1", new BigDecimal("-250000"), new BigDecimal("0.000000"), OK),
                new TradeCalculation(
                    "IE00F2", new BigDecimal("-210000"), new BigDecimal("0.000000"), OK),
                new TradeCalculation(
                    "IE00SLOW", new BigDecimal("-40000"), new BigDecimal("1.000000"), OK)));
  }

  @Test
  void sell_lastResortFullyLiquidatesTrappedSubThresholdOddLotsMostOverweightFirst() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00BIG1", new BigDecimal("300000")),
                    new PositionSnapshot("IE00BIG2", new BigDecimal("300000")),
                    new PositionSnapshot("IE00ODD1", new BigDecimal("40000")),
                    new PositionSnapshot("IE00ODD2", new BigDecimal("40000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00BIG1", new BigDecimal("0.30")),
                    new ModelWeight("IE00BIG2", new BigDecimal("0.30")),
                    new ModelWeight("IE00ODD1", new BigDecimal("0.20")),
                    new ModelWeight("IE00ODD2", new BigDecimal("0.20"))))
            .grossPortfolioValue(new BigDecimal("680000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-660000"))
            .minTransactionThreshold(new BigDecimal("50000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    assertThat(result.trades())
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            List.of(
                new TradeCalculation(
                    "IE00BIG1", new BigDecimal("-300000"), new BigDecimal("0.000000"), OK),
                new TradeCalculation(
                    "IE00BIG2", new BigDecimal("-300000"), new BigDecimal("0.000000"), OK),
                new TradeCalculation(
                    "IE00ODD1", new BigDecimal("-40000"), new BigDecimal("0.000000"), OK),
                new TradeCalculation(
                    "IE00ODD2", new BigDecimal("-40000"), new BigDecimal("0.000000"), OK)));
  }

  @Test
  void sellFast_safetySpillFulfilsRedemptionTheBucketsStrandBelowMinTrade() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00FASTBIG", new BigDecimal("86000")),
                    new PositionSnapshot("IE00FASTODD1", new BigDecimal("4000")),
                    new PositionSnapshot("IE00FASTODD2", new BigDecimal("3000")),
                    new PositionSnapshot("IE00SLOWODD", new BigDecimal("2000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00FASTBIG", new BigDecimal("3")),
                    new ModelWeight("IE00FASTODD1", new BigDecimal("5")),
                    new ModelWeight("IE00FASTODD2", new BigDecimal("4")),
                    new ModelWeight("IE00SLOWODD", new BigDecimal("4"))))
            .grossPortfolioValue(new BigDecimal("95000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-4816"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of("IE00FASTBIG", "IE00FASTODD1", "IE00FASTODD2"))
            .build();

    var result = engine.calculate(input, SELL_FAST);

    assertThat(result.trades())
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            List.of(
                new TradeCalculation("IE00FASTBIG", ZERO, new BigDecimal("0.905263"), OK),
                new TradeCalculation(
                    "IE00FASTODD1", new BigDecimal("-4000"), new BigDecimal("0.000000"), OK),
                new TradeCalculation("IE00FASTODD2", ZERO, new BigDecimal("0.031579"), OK),
                new TradeCalculation(
                    "IE00SLOWODD", new BigDecimal("-2000"), new BigDecimal("0.000000"), OK)));
  }

  @Test
  void sell_withoutSoftLimitsSpreadsSellsAcrossOverweightFundsTowardModelRatherThanConcentrating() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("86151")),
                    new PositionSnapshot("IE00B", new BigDecimal("262989")),
                    new PositionSnapshot("IE00C", new BigDecimal("93572"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", ZERO),
                    new ModelWeight("IE00B", new BigDecimal("0.50")),
                    new ModelWeight("IE00C", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("442712"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-354775"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    assertThat(result.trades())
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            List.of(
                new TradeCalculation(
                    "IE00A", new BigDecimal("-86151"), new BigDecimal("0.000000"), OK),
                new TradeCalculation(
                    "IE00B", new BigDecimal("-201180.01"), new BigDecimal("0.139614"), OK),
                new TradeCalculation(
                    "IE00C", new BigDecimal("-67443.99"), new BigDecimal("0.059018"), OK)));

    BigDecimal totalSold =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalSold).isEqualByComparingTo(new BigDecimal("-354775"));
  }

  @Test
  void sell_withFreeCashExactlyAtNegativePointZeroOne_recordsNoCashShortfallReason() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("10000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-0.01"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    assertThat(result.noTradeReason())
        .isEqualTo("No trades: mode=SELL, reason=noCashShortfall, freeCash=-0.01");
  }

  @Test
  void sell_withShortfallOneCentBeyondSellableMarketValue_stillFullyLiquidatesWithoutThrowing() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("40000"))))
            .modelWeights(List.of())
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-40000.01"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                    new PositionLimitSnapshot(new BigDecimal("0.99"), new BigDecimal("0.999"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    assertThat(result.trades().getFirst().tradeAmount())
        .isEqualByComparingTo(new BigDecimal("-40000.00"));
  }

  @Test
  void sell_withCapExactlyAtThresholdTolerance_stillSellsInsteadOfBeingExcluded() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("99.99"))))
            .modelWeights(List.of())
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-200"))
            .minTransactionThreshold(new BigDecimal("100"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    assertThat(result.trades().getFirst().tradeAmount())
        .isEqualByComparingTo(new BigDecimal("-99.99"));
  }

  @Test
  void sell_withATiedSubThresholdSplit_eliminatesTheFirstTiedPositionNotTheLast() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("1000000")),
                    new PositionSnapshot("IE00B", new BigDecimal("1000000"))))
            .modelWeights(List.of())
            .grossPortfolioValue(new BigDecimal("2000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-1500"))
            .minTransactionThreshold(new BigDecimal("1000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();
    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(ZERO);
    assertThat(tradeB.tradeAmount()).isEqualByComparingTo(new BigDecimal("-1500.00"));
  }

  @Test
  void sell_withAmountExactlyAtThresholdTolerance_stillAllocatesInsteadOfDroppingTheRunner() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("1000000"))))
            .modelWeights(List.of())
            .grossPortfolioValue(new BigDecimal("2000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-99.99"))
            .minTransactionThreshold(new BigDecimal("100"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    assertThat(result.trades().getFirst().tradeAmount())
        .isEqualByComparingTo(new BigDecimal("-99.99"));
  }
}
