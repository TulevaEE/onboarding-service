package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.transaction.CalculationWarningType.REBALANCE_NET_CASH_MISMATCH;
import static ee.tuleva.onboarding.investment.transaction.CalculationWarningType.REBALANCE_NET_NOT_ACHIEVED;
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

class TradeCalculationEngineRebalanceTest {

  private final TradeCalculationEngine engine = new TradeCalculationEngine();

  @Test
  void rebalance_withNoDrift_recordsNoDriftReason() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("1000000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    assertThat(result.noTradeReason())
        .isEqualTo("No trades: mode=REBALANCE, reason=noDriftBeyondThreshold");
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
  void rebalance_eliminatesSubThresholdTradesAndRedistributes() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("350000")),
                    new PositionSnapshot("IE00B", new BigDecimal("412000")),
                    new PositionSnapshot("IE00C", new BigDecimal("188000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.40")),
                    new ModelWeight("IE00B", new BigDecimal("0.40")),
                    new ModelWeight("IE00C", new BigDecimal("0.20"))))
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
    var tradeC =
        result.trades().stream().filter(t -> t.isin().equals("IE00C")).findFirst().orElseThrow();

    assertThat(tradeC.tradeAmount()).isEqualByComparingTo(ZERO);
    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(new BigDecimal("32000.00"));
    assertThat(tradeB.tradeAmount()).isEqualByComparingTo(new BigDecimal("-32000.00"));
  }

  @Test
  void rebalance_producesNoBuysWhenNoPositionIsUnderTarget() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("600000")),
                    new PositionSnapshot("IE00B", new BigDecimal("500000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
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

    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(new BigDecimal("-100000"));
    assertThat(tradeB.tradeAmount()).isEqualByComparingTo(ZERO);
  }

  @Test
  void rebalance_neverSellsAnyPositionBeyondItsMarketValue() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("100000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("100000"))
            .cashBuffer(ZERO)
            .liabilities(new BigDecimal("150000"))
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();

    assertThat(tradeA.tradeAmount()).isGreaterThanOrEqualTo(new BigDecimal("-100000"));
  }

  @Test
  void rebalance_neverInflatesSurvivingSellAllocationBeyondMarketValueAfterThresholdElimination() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("3000")),
                    new PositionSnapshot("IE00B", new BigDecimal("100000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("103000"))
            .cashBuffer(ZERO)
            .liabilities(new BigDecimal("203000"))
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();

    assertThat(tradeB.tradeAmount()).isGreaterThanOrEqualTo(new BigDecimal("-100000"));
  }

  @Test
  void rebalance_waterFillsHardLimitBuyExcessAcrossRunnersInsteadOfStrandingCash() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("100000")),
                    new PositionSnapshot("IE00B", new BigDecimal("100000")),
                    new PositionSnapshot("IE00C", new BigDecimal("300000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.30")),
                    new ModelWeight("IE00C", new BigDecimal("0.20"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                    new PositionLimitSnapshot(new BigDecimal("0.32"), new BigDecimal("0.32"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    BigDecimal totalBought =
        result.trades().stream()
            .map(TradeCalculation::tradeAmount)
            .filter(amount -> amount.compareTo(ZERO) > 0)
            .reduce(ZERO, BigDecimal::add);

    assertThat(totalBought).isEqualByComparingTo(new BigDecimal("600000"));
    assertThat(result.trades())
        .allSatisfy(trade -> assertThat(trade.limitStatus()).isNotEqualTo(HARD_LIMIT_EXCEEDED));
  }

  @Test
  void rebalance_whenModelNetDivergesFromFreeCash_warnsToCheckTheInputs() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("600000")),
                    new PositionSnapshot("IE00B", new BigDecimal("400000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("1100000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("40000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    assertThat(result.warnings())
        .extracting(CalculationWarning::type)
        .containsExactly(REBALANCE_NET_CASH_MISMATCH);
  }

  @Test
  void rebalance_whenModelNetMatchesFreeCash_raisesNoWarning() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("600000")),
                    new PositionSnapshot("IE00B", new BigDecimal("400000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("1100000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    assertThat(result.warnings()).isEmpty();
  }

  @Test
  void rebalance_whenTheGapIsExactlyTheMinTransactionThreshold_stillWarns() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("600000")),
                    new PositionSnapshot("IE00B", new BigDecimal("400000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("1100000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("95000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    assertThat(result.warnings())
        .extracting(CalculationWarning::type)
        .containsExactly(REBALANCE_NET_CASH_MISMATCH);
  }

  @Test
  void rebalance_whenAHardLimitBlocksPartOfTheBuySide_warnsTheNetWasNotAchieved() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("100000")),
                    new PositionSnapshot("IE00B", new BigDecimal("900000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                    new PositionLimitSnapshot(new BigDecimal("0.20"), new BigDecimal("0.20"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    assertThat(result.warnings())
        .extracting(CalculationWarning::type)
        .containsExactly(REBALANCE_NET_NOT_ACHIEVED);
  }

  @Test
  void rebalance_withoutAnyPositions_raisesNoCashWarnings() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of())
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(ZERO)
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    assertThat(engine.calculate(input, REBALANCE).warnings()).isEmpty();
  }
}
