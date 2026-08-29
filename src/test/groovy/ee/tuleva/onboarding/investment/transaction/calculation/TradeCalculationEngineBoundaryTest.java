package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.transaction.CalculationWarningType.REBALANCE_NET_NOT_ACHIEVED;
import static ee.tuleva.onboarding.investment.transaction.LimitStatus.HARD_LIMIT_EXCEEDED;
import static ee.tuleva.onboarding.investment.transaction.LimitStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.REBALANCE;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.SELL;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.SELL_FAST;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.CalculationWarning;
import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import ee.tuleva.onboarding.investment.transaction.ModelWeight;
import ee.tuleva.onboarding.investment.transaction.PositionLimitSnapshot;
import ee.tuleva.onboarding.investment.transaction.PositionSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TradeCalculationEngineBoundaryTest {

  private final TradeCalculationEngine engine = new TradeCalculationEngine();

  @Test
  void rebalance_positionExactlyAtHardLimitWithNoDriftIsNotFlaggedHardLimitExceeded() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("600000")),
                    new PositionSnapshot("IE00B", new BigDecimal("400000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.60")),
                    new ModelWeight("IE00B", new BigDecimal("0.40"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                    new PositionLimitSnapshot(new BigDecimal("0.60"), new BigDecimal("0.60"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(ZERO);
    assertThat(tradeA.limitStatus()).isEqualTo(OK);
  }

  @Test
  void rebalance_positionOneBasisPointOverHardLimitWithNoDriftIsFlaggedWithoutClippingZeroTrade() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("600000")),
                    new PositionSnapshot("IE00B", new BigDecimal("400000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.60")),
                    new ModelWeight("IE00B", new BigDecimal("0.40"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                    new PositionLimitSnapshot(new BigDecimal("0.5999"), new BigDecimal("0.5999"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(ZERO);
    assertThat(tradeA.limitStatus()).isEqualTo(HARD_LIMIT_EXCEEDED);
  }

  @Test
  void rebalance_positionExactlyAtSoftLimitWithNoDriftIsNotFlaggedSoftLimitExceeded() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("600000")),
                    new PositionSnapshot("IE00B", new BigDecimal("400000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.60")),
                    new ModelWeight("IE00B", new BigDecimal("0.40"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                    new PositionLimitSnapshot(new BigDecimal("0.60"), new BigDecimal("0.99"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    assertThat(tradeA.limitStatus()).isEqualTo(OK);
  }

  @Test
  void buy_withZeroGrossPortfolioValueDoesNotDivideByZeroAndYieldsZeroProjectedWeight() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("500")),
                    new PositionSnapshot("IE00B", new BigDecimal("300"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.60")),
                    new ModelWeight("IE00B", new BigDecimal("0.40"))))
            .grossPortfolioValue(ZERO)
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("1000"))
            .minTransactionThreshold(new BigDecimal("100"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();
    assertThat(tradeB.tradeAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat(tradeB.projectedWeight()).isEqualByComparingTo(ZERO);
  }

  @Test
  void sell_freeCashExactlyAtNegativePointZeroOneStillReturnsTradeListSizedToPositions() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("10000")),
                    new PositionSnapshot("IE00B", new BigDecimal("20000"))))
            .modelWeights(List.of())
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-0.01"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    assertThat(result.trades()).hasSize(2);
    assertThat(result.trades())
        .allSatisfy(trade -> assertThat(trade.tradeAmount()).isEqualByComparingTo(ZERO));
  }

  @Test
  void sellFast_freeCashExactlyAtNegativePointZeroOneStillReturnsTradeListSizedToPositions() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("10000")),
                    new PositionSnapshot("IE00B", new BigDecimal("20000"))))
            .modelWeights(List.of())
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-0.01"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of("IE00A"))
            .build();

    var result = engine.calculate(input, SELL_FAST);

    assertThat(result.trades()).hasSize(2);
    assertThat(result.trades())
        .allSatisfy(trade -> assertThat(trade.tradeAmount()).isEqualByComparingTo(ZERO));
  }

  @Test
  void rebalance_bucketTotalExactlyAtMinMeaningfulAmountSkipsDistribution() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("999999.99")),
                    new PositionSnapshot("IE00B", new BigDecimal("1000000.00"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("2000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("0.02"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(ZERO);
  }

  @Test
  void rebalance_bucketTotalOneCentAboveMinMeaningfulAmountProceedsWithDistribution() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("999999.98")),
                    new PositionSnapshot("IE00B", new BigDecimal("1000000.00"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.50"))))
            .grossPortfolioValue(new BigDecimal("2000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("0.02"))
            .positionLimits(Map.of())
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(new BigDecimal("0.02"));
  }

  @Test
  void rebalance_achievedNetGapExactlyAtThresholdStillWarnsNetNotAchieved() {
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
                    new PositionLimitSnapshot(new BigDecimal("0.4951"), new BigDecimal("0.4951"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    assertThat(result.warnings())
        .extracting(CalculationWarning::type)
        .containsExactly(REBALANCE_NET_NOT_ACHIEVED);
  }

  @Test
  void rebalance_achievedNetGapOneCentBelowThresholdRaisesNoWarning() {
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
                    new PositionLimitSnapshot(
                        new BigDecimal("0.49510001"), new BigDecimal("0.49510001"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, REBALANCE);

    assertThat(result.warnings()).isEmpty();
  }

  @Test
  void buy_topUpDonorSwapSucceedsWhenDonorStaysAboveThresholdAfterGiving() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("400000")),
                    new PositionSnapshot("IE00B", new BigDecimal("15000")),
                    new PositionSnapshot("IE00C", new BigDecimal("100000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.50")),
                    new ModelWeight("IE00B", new BigDecimal("0.02")),
                    new ModelWeight("IE00C", new BigDecimal("0.48"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("300000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                        new PositionLimitSnapshot(
                            new BigDecimal("0.4401"), new BigDecimal("0.4401")),
                    "IE00C",
                        new PositionLimitSnapshot(
                            new BigDecimal("0.3581"), new BigDecimal("0.3581"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();
    var tradeC =
        result.trades().stream().filter(t -> t.isin().equals("IE00C")).findFirst().orElseThrow();

    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(new BigDecimal("40000.00"));
    assertThat(tradeB.tradeAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));
    assertThat(tradeC.tradeAmount()).isEqualByComparingTo(new BigDecimal("255000.00"));
  }

  @Test
  void buy_topUpDonorSwapFailsWhenDonorWouldDropBelowThresholdAfterGiving() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00G", ZERO),
                    new PositionSnapshot("IE00B", ZERO),
                    new PositionSnapshot("IE00D", ZERO)))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00G", new BigDecimal("0.059")),
                    new ModelWeight("IE00B", new BigDecimal("0.00001")),
                    new ModelWeight("IE00D", new BigDecimal("0.065"))))
            .grossPortfolioValue(new BigDecimal("100000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("12400"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00G",
                        new PositionLimitSnapshot(
                            new BigDecimal("0.0551"), new BigDecimal("0.0551")),
                    "IE00D",
                        new PositionLimitSnapshot(
                            new BigDecimal("0.0651"), new BigDecimal("0.0651"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var tradeG =
        result.trades().stream().filter(t -> t.isin().equals("IE00G")).findFirst().orElseThrow();
    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();
    var tradeD =
        result.trades().stream().filter(t -> t.isin().equals("IE00D")).findFirst().orElseThrow();

    assertThat(tradeG.tradeAmount()).isEqualByComparingTo(new BigDecimal("5500.00"));
    assertThat(tradeB.tradeAmount()).isEqualByComparingTo(ZERO);
    assertThat(tradeD.tradeAmount()).isEqualByComparingTo(new BigDecimal("6500.00"));
  }

  @Test
  void sell_excludesPositionExactlyAtSoftLimitFromOverSoftReliefPool() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("600000")),
                    new PositionSnapshot("IE00B", new BigDecimal("200000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.55")),
                    new ModelWeight("IE00B", new BigDecimal("0.15"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-50000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                        new PositionLimitSnapshot(new BigDecimal("0.10"), new BigDecimal("0.99")),
                    "IE00B",
                        new PositionLimitSnapshot(new BigDecimal("0.20"), new BigDecimal("0.99"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();

    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(new BigDecimal("-50000"));
    assertThat(tradeB.tradeAmount()).isEqualByComparingTo(ZERO);
  }

  @Test
  void sell_includesPositionOneBasisPointOverSoftLimitInOverSoftReliefPool() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("600000")),
                    new PositionSnapshot("IE00B", new BigDecimal("200000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.55")),
                    new ModelWeight("IE00B", new BigDecimal("0.15"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(new BigDecimal("-50000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                        new PositionLimitSnapshot(new BigDecimal("0.10"), new BigDecimal("0.99")),
                    "IE00B",
                        new PositionLimitSnapshot(
                            new BigDecimal("0.1999"), new BigDecimal("0.99"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, SELL);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    var tradeB =
        result.trades().stream().filter(t -> t.isin().equals("IE00B")).findFirst().orElseThrow();

    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(new BigDecimal("-25000.00"));
    assertThat(tradeB.tradeAmount()).isEqualByComparingTo(new BigDecimal("-25000.00"));
  }

  @Test
  void buy_includesPositionWithHeadroomExactlyAtThreshold() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("595000")),
                    new PositionSnapshot("IE00B", new BigDecimal("200000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.65")),
                    new ModelWeight("IE00B", new BigDecimal("0.30"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                    new PositionLimitSnapshot(new BigDecimal("0.55"), new BigDecimal("0.6001"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(new BigDecimal("5000.00"));
  }

  @Test
  void buy_excludesPositionWithHeadroomOneCentBelowThreshold() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("595000")),
                    new PositionSnapshot("IE00B", new BigDecimal("200000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.65")),
                    new ModelWeight("IE00B", new BigDecimal("0.30"))))
            .grossPortfolioValue(new BigDecimal("1000000"))
            .cashBuffer(new BigDecimal("50000"))
            .liabilities(ZERO)
            .freeCash(new BigDecimal("100000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .positionLimits(
                Map.of(
                    "IE00A",
                    new PositionLimitSnapshot(
                        new BigDecimal("0.55"), new BigDecimal("0.60009999"))))
            .fastSellIsins(Set.of())
            .build();

    var result = engine.calculate(input, BUY);

    var tradeA =
        result.trades().stream().filter(t -> t.isin().equals("IE00A")).findFirst().orElseThrow();
    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(ZERO);
  }

  @Test
  void buy_zeroPositionMarketValueIsAcceptedWithoutThrowing() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", ZERO)))
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

    assertThat(result.trades().getFirst().tradeAmount())
        .isEqualByComparingTo(new BigDecimal("100000"));
  }

  @Test
  void rebalance_zeroTotalModelWeightUsesOneAsNormalizerWithoutThrowing() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00A", new BigDecimal("500000")),
                    new PositionSnapshot("IE00B", new BigDecimal("500000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", ZERO), new ModelWeight("IE00B", ZERO)))
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
    assertThat(tradeA.tradeAmount()).isEqualByComparingTo(new BigDecimal("-500000.00"));
  }
}
