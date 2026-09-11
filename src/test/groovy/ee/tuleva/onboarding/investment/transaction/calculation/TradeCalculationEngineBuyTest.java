package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.investment.transaction.LimitStatus.*;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.*;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TradeCalculationEngineBuyTest {

  private final TradeCalculationEngine engine = new TradeCalculationEngine();

  @Test
  void buy_withFreeCashBelowThreshold_recordsFreeCashNoTradeReason() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("500000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("1000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    assertThat(result.trades())
        .allSatisfy(trade -> assertThat(trade.tradeAmount()).isEqualByComparingTo(ZERO));
    assertThat(result.noTradeReason())
        .isEqualTo(
            "No trades: mode=BUY, reason=freeCashBelowMinTransactionThreshold, freeCash=1000,"
                + " minTransactionThreshold=5000");
  }

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
    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(new BigDecimal("19900.00"));
    assertThat(tradeA.projectedWeight()).isLessThanOrEqualTo(new BigDecimal("0.6001"));
    assertThat(tradeA.limitStatus()).isEqualTo(SOFT_LIMIT_EXCEEDED);

    BigDecimal totalInvested =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalInvested)
        .isCloseTo(new BigDecimal("200000"), org.assertj.core.data.Offset.offset(BigDecimal.ONE));
  }

  @Test
  void buy_excludesInstrumentsWithHeadroomBelowThresholdAndInvestsFullAmount() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("595000")),
                    new PositionSnapshot("IE00B", new BigDecimal("200000")),
                    new PositionSnapshot("IE00C", new BigDecimal("100000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.65")),
                    new ModelWeight("IE00B", new BigDecimal("0.20")),
                    new ModelWeight("IE00C", new BigDecimal("0.15"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                    new PositionLimitSnapshot(new BigDecimal("0.55"), new BigDecimal("0.60"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(ZERO);

    BigDecimal totalInvested =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalInvested)
        .isCloseTo(new BigDecimal("100000"), org.assertj.core.data.Offset.offset(BigDecimal.ONE));
  }

  @Test
  void buy_redistributesHardLimitExcessToRunnerUpInstruments() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("270000")),
                    new PositionSnapshot("IE00B", new BigDecimal("300000")),
                    new PositionSnapshot("IE00C", new BigDecimal("200000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.35")),
                    new ModelWeight("IE00B", new BigDecimal("0.40")),
                    new ModelWeight("IE00C", new BigDecimal("0.25"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("180000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                    new PositionLimitSnapshot(new BigDecimal("0.28"), new BigDecimal("0.30"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    assertThat(tradeA.tradeAmount())
        .isCloseTo(new BigDecimal("29900"), org.assertj.core.data.Offset.offset(BigDecimal.ONE));

    BigDecimal totalInvested =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalInvested)
        .isCloseTo(new BigDecimal("180000"), org.assertj.core.data.Offset.offset(BigDecimal.ONE));
  }

  @Test
  void buy_waterFillsHardLimitExcessAcrossRunnersInsteadOfStrandingCash() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00X", new BigDecimal("240000")),
                    new PositionSnapshot("IE00P", new BigDecimal("130000")),
                    new PositionSnapshot("IE00Q", new BigDecimal("130000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00X", new BigDecimal("0.60")),
                    new ModelWeight("IE00P", new BigDecimal("0.20")),
                    new ModelWeight("IE00Q", new BigDecimal("0.16"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00X",
                        new PositionLimitSnapshot(new BigDecimal("0.25"), new BigDecimal("0.26")),
                    "IE00P",
                        new PositionLimitSnapshot(new BigDecimal("0.14"), new BigDecimal("0.15")),
                    "IE00Q",
                        new PositionLimitSnapshot(new BigDecimal("0.39"), new BigDecimal("0.40"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    BigDecimal totalInvested =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalInvested)
        .isCloseTo(new BigDecimal("100000"), org.assertj.core.data.Offset.offset(BigDecimal.ONE));

    result
        .trades()
        .forEach(
            trade ->
                assertThat(trade.projectedWeight())
                    .isLessThanOrEqualTo(input.positionLimits().get(trade.isin()).hardLimit()));
  }

  @Test
  void buy_topsUpBestRunnerWithSubThresholdHardLimitLeftover() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("400000")),
                    new PositionSnapshot("IE00B", new BigDecimal("400000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("50000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                    new PositionLimitSnapshot(new BigDecimal("0.40"), new BigDecimal("0.4221"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();

    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(new BigDecimal("22000.00"));
    assertThat(tradeB.tradeAmount()).isEqualByComparingTo(new BigDecimal("28000.00"));

    BigDecimal totalInvested =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalInvested).isEqualByComparingTo(new BigDecimal("50000.00"));
  }

  @Test
  void buy_strandsExcessWhenEveryRunnerHitsItsHardLimit() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("100000")),
                    new PositionSnapshot("IE00B", new BigDecimal("50000")),
                    new PositionSnapshot("IE00C", new BigDecimal("50000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.80")),
                    new ModelWeight("IE00B", new BigDecimal("0.10")),
                    new ModelWeight("IE00C", new BigDecimal("0.10"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                        new PositionLimitSnapshot(new BigDecimal("0.10"), new BigDecimal("0.13")),
                    "IE00B",
                        new PositionLimitSnapshot(new BigDecimal("0.05"), new BigDecimal("0.056")),
                    "IE00C",
                        new PositionLimitSnapshot(new BigDecimal("0.05"), new BigDecimal("0.056"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();
    var tradeC =
        result.trades().stream().filter(t -> t.isin().equals("IE00C")).findFirst().orElseThrow();

    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(new BigDecimal("29900.00"));
    assertThat(tradeB.tradeAmount()).isEqualByComparingTo(new BigDecimal("5900.00"));
    assertThat(tradeC.tradeAmount()).isEqualByComparingTo(new BigDecimal("5900.00"));

    BigDecimal totalInvested =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalInvested).isEqualByComparingTo(new BigDecimal("41700.00"));
    assertThat(totalInvested).isLessThan(input.freeCash());

    result
        .trades()
        .forEach(
            trade ->
                assertThat(trade.projectedWeight())
                    .isLessThanOrEqualTo(input.positionLimits().get(trade.isin()).hardLimit()));
  }

  @Test
  void buy_stopsWaterFillOnceLeftoverExcessIsFullyAbsorbed() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("300000")),
                    new PositionSnapshot("IE00B", new BigDecimal("300000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.60")),
                    new ModelWeight("IE00B", new BigDecimal("0.40"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("50000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                        new PositionLimitSnapshot(new BigDecimal("0.30"), new BigDecimal("0.31")),
                    "IE00B",
                        new PositionLimitSnapshot(
                            new BigDecimal("0.33"), new BigDecimal("0.3402"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();

    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(new BigDecimal("9900.00"));
    assertThat(tradeB.tradeAmount()).isEqualByComparingTo(new BigDecimal("40100.00"));

    BigDecimal totalInvested =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalInvested).isEqualByComparingTo(new BigDecimal("50000.00"));
  }

  @Test
  void buy_ignoresOnTargetPositionsWhenRedistributingHardLimitExcess() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("350000")),
                    new PositionSnapshot("IE00B", new BigDecimal("200000")),
                    new PositionSnapshot("IE00C", new BigDecimal("300000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.40")),
                    new ModelWeight("IE00B", new BigDecimal("0.30")),
                    new ModelWeight("IE00C", new BigDecimal("0.30"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("50000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                    new PositionLimitSnapshot(new BigDecimal("0.35"), new BigDecimal("0.36"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();
    var tradeC =
        result.trades().stream().filter(t -> t.isin().equals("IE00C")).findFirst().orElseThrow();

    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(new BigDecimal("9900.00"));
    assertThat(tradeB.tradeAmount()).isEqualByComparingTo(new BigDecimal("40100.00"));
    assertThat(tradeC.tradeAmount()).isEqualByComparingTo(ZERO);

    BigDecimal totalInvested =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalInvested).isEqualByComparingTo(new BigDecimal("50000.00"));
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
  void buy_deploysFreeCashWhenAllPositionsAbovePostCashTarget() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("245000")),
                    new PositionSnapshot("IE00B", new BigDecimal("245000")),
                    new PositionSnapshot("IE00C", new BigDecimal("245000")),
                    new PositionSnapshot("IE00D", new BigDecimal("245000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.25")),
                    new ModelWeight("IE00B", new BigDecimal("0.25")),
                    new ModelWeight("IE00C", new BigDecimal("0.25")),
                    new ModelWeight("IE00D", new BigDecimal("0.25"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("20000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    BigDecimal totalBuy =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalBuy)
        .isCloseTo(new BigDecimal("20000"), org.assertj.core.data.Offset.offset(BigDecimal.ONE));
  }

  @Test
  void buy_fallbackPrefersLeastOverweightFund() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("238000")),
                    new PositionSnapshot("IE00B", new BigDecimal("250000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("500000"))
            .cashBuffer(new BigDecimal("25000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("12000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();

    assertThat(tradeA.tradeAmount()).isGreaterThan(tradeB.tradeAmount());

    BigDecimal totalBuy =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalBuy)
        .isCloseTo(new BigDecimal("12000"), org.assertj.core.data.Offset.offset(BigDecimal.ONE));
  }

  @Test
  void buy_includesReceivablesInTargetBaseForScoring() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("90000")),
                    new PositionSnapshot("IE00B", new BigDecimal("80000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("170000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .receivables(new BigDecimal("60000"))
            .freeCash(new BigDecimal("20000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();

    assertThat(tradeA.tradeAmount()).isGreaterThan(ZERO);
    assertThat(tradeB.tradeAmount()).isGreaterThan(tradeA.tradeAmount());

    BigDecimal totalBuy =
        result.trades().stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    assertThat(totalBuy)
        .isCloseTo(new BigDecimal("20000"), org.assertj.core.data.Offset.offset(BigDecimal.ONE));
  }

  @Test
  void buy_concentratesFreeCashIntoSingleAboveThresholdTradeInsteadOfSubThresholdSplits() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("315900")),
                    new PositionSnapshot("IE00B", new BigDecimal("316100")),
                    new PositionSnapshot("IE00C", new BigDecimal("368000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.32")),
                    new ModelWeight("IE00B", new BigDecimal("0.32")),
                    new ModelWeight("IE00C", new BigDecimal("0.36"))))
            .grossPortfolioValue(new BigDecimal("1050000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("50000"))
            .minTransactionThreshold(new BigDecimal("50000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    assertThat(result.trades())
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(
            List.of(
                new TradeCalculation(
                    "IE00A", new BigDecimal("50000.00"), new BigDecimal("0.348476"), OK),
                new TradeCalculation("IE00B", ZERO, new BigDecimal("0.301048"), OK),
                new TradeCalculation("IE00C", ZERO, new BigDecimal("0.350476"), OK)));
  }

  @Test
  void buy_withNoPositions_recordsNoPositionsReason() {
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

    assertThat(result.noTradeReason()).isEqualTo("No trades: mode=BUY, reason=noPositions");
  }

  @Test
  void buy_withFreeCashExactlyAtThresholdButNoHeadroom_recordsNoPositionUnderTargetReason() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("400000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("500000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("5000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                    new PositionLimitSnapshot(new BigDecimal("0.40"), new BigDecimal("0.4001"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    assertThat(result.noTradeReason())
        .isEqualTo("No trades: mode=BUY, reason=noPositionUnderTargetBeyondThreshold");
  }
}
