package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.investment.transaction.TransactionMode.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.REBALANCE;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.SELL;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.SELL_FAST;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import ee.tuleva.onboarding.investment.transaction.FundCalculationResult;
import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import ee.tuleva.onboarding.investment.transaction.ModelWeight;
import ee.tuleva.onboarding.investment.transaction.PositionSnapshot;
import ee.tuleva.onboarding.investment.transaction.TradeCalculation;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TradeCalculationEngineExclusionTest {

  private static final List<ModelWeight> MODEL =
      List.of(
          new ModelWeight("IE00A", new BigDecimal("0.60")),
          new ModelWeight("IE00B", new BigDecimal("0.30")),
          new ModelWeight("IE00C", new BigDecimal("0.10")));

  private final TradeCalculationEngine engine = new TradeCalculationEngine();

  @Test
  void buy_withAnInstrumentExcluded_givesItsShareToTheOthersAndBuysTheSameAmount() {
    var input = fund("540000", "270000", "90000", "100000", ZERO);

    var withoutExclusion = engine.calculate(input, BUY);
    var withExclusion = engine.calculate(input.excluding(Set.of("IE00C")), BUY);

    assertThat(tradeAmounts(withoutExclusion))
        .containsExactly(
            entry("IE00A", new BigDecimal("60000.00")),
            entry("IE00B", new BigDecimal("30000.00")),
            entry("IE00C", new BigDecimal("10000.00")));
    assertThat(tradeAmounts(withExclusion))
        .containsExactly(
            entry("IE00A", new BigDecimal("66666.67")), entry("IE00B", new BigDecimal("33333.33")));
    assertThat(withExclusion.netInvestable()).isEqualByComparingTo("910000");
  }

  @Test
  void rebalance_withAnInstrumentExcluded_leavesItUntouchedAndTradesTheSameNet() {
    var input = fund("600000", "200000", "150000", "50000", ZERO);

    var withoutExclusion = engine.calculate(input, REBALANCE);
    var withExclusion = engine.calculate(input.excluding(Set.of("IE00C")), REBALANCE);

    assertThat(tradeAmounts(withoutExclusion))
        .containsExactly(
            entry("IE00A", new BigDecimal("0.00")),
            entry("IE00B", new BigDecimal("100000.00")),
            entry("IE00C", new BigDecimal("-50000.00")));
    assertThat(tradeAmounts(withExclusion))
        .containsExactly(
            entry("IE00A", new BigDecimal("-33333.33")),
            entry("IE00B", new BigDecimal("83333.33")));
    assertThat(netTraded(withExclusion)).isEqualByComparingTo(netTraded(withoutExclusion));
    assertThat(withExclusion.warnings()).isEmpty();
  }

  @Test
  void sell_withAnInstrumentExcluded_sellsTheSameTotalFromTheOthers() {
    var input = fund("600000", "300000", "100000", "0", new BigDecimal("100000"));

    var withoutExclusion = engine.calculate(input, SELL);
    var withExclusion = engine.calculate(input.excluding(Set.of("IE00C")), SELL);

    assertThat(tradeAmounts(withoutExclusion))
        .containsExactly(
            entry("IE00A", new BigDecimal("-60000.00")),
            entry("IE00B", new BigDecimal("-30000.00")),
            entry("IE00C", new BigDecimal("-10000.00")));
    assertThat(tradeAmounts(withExclusion))
        .containsExactly(
            entry("IE00A", new BigDecimal("-66666.67")),
            entry("IE00B", new BigDecimal("-33333.33")));
  }

  @Test
  void sellFast_withTheFastSellInstrumentExcluded_sellsTheSameTotalFromTheOthers() {
    var input =
        withFastSell(
            fund("600000", "300000", "100000", "0", new BigDecimal("100000")), Set.of("IE00C"));

    var withExclusion = engine.calculate(input.excluding(Set.of("IE00C")), SELL_FAST);

    assertThat(tradeAmounts(withExclusion)).doesNotContainKey("IE00C");
    assertThat(netTraded(withExclusion)).isEqualByComparingTo("-100000");
  }

  @Test
  void sellFast_withAnExclusionThatLeavesTooLittleToSell_isRejected() {
    var input = fund("600000", "300000", "100000", "0", new BigDecimal("500000"));

    assertThatThrownBy(() -> engine.calculate(input.excluding(Set.of("IE00A")), SELL_FAST))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void sell_withAnExclusionThatLeavesTooLittleToSell_isRejected() {
    var input = fund("600000", "300000", "100000", "0", new BigDecimal("500000"));

    assertThatThrownBy(() -> engine.calculate(input.excluding(Set.of("IE00A")), SELL))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void sellAndSellFast_withEveryHoldingExcluded_areRejectedWhenThereIsAShortfall() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00C", new BigDecimal("100000"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("100000"))
            .cashBuffer(ZERO)
            .liabilities(new BigDecimal("50000"))
            .freeCash(new BigDecimal("-50000"))
            .minTransactionThreshold(new BigDecimal("5000"))
            .build()
            .excluding(Set.of("IE00C"));

    assertThatThrownBy(() -> engine.calculate(input, SELL))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> engine.calculate(input, SELL_FAST))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void sellAndSellFast_whoseSellsFallShortOfTheShortfallAfterAnExclusion_areRejected() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(
                List.of(
                    new PositionSnapshot("IE00OFF", new BigDecimal("400000")),
                    new PositionSnapshot("IE00B", new BigDecimal("1000000")),
                    new PositionSnapshot("IE00C", new BigDecimal("600000"))))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00B", new BigDecimal("0.80")),
                    new ModelWeight("IE00C", new BigDecimal("0.20"))))
            .grossPortfolioValue(new BigDecimal("2000000"))
            .cashBuffer(ZERO)
            .liabilities(new BigDecimal("440000"))
            .freeCash(new BigDecimal("-440000"))
            .minTransactionThreshold(new BigDecimal("50000"))
            .build();

    assertThat(netTraded(engine.calculate(input, SELL))).isEqualByComparingTo("-440000");
    assertThatThrownBy(() -> engine.calculate(input.excluding(Set.of("IE00C")), SELL))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> engine.calculate(input.excluding(Set.of("IE00C")), SELL_FAST))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void anExcludedHoldingWithANegativeMarketValue_isRejected() {
    var input =
        FundTransactionInput.builder()
            .fund(TUV100)
            .positions(List.of(new PositionSnapshot("IE00A", new BigDecimal("600000"))))
            .excludedPositions(List.of(new PositionSnapshot("IE00C", new BigDecimal("-1"))))
            .modelWeights(List.of(new ModelWeight("IE00A", new BigDecimal("1.00"))))
            .grossPortfolioValue(new BigDecimal("600000"))
            .cashBuffer(ZERO)
            .liabilities(ZERO)
            .freeCash(ZERO)
            .minTransactionThreshold(new BigDecimal("5000"))
            .build();

    assertThatThrownBy(() -> engine.calculate(input, REBALANCE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static FundTransactionInput fund(
      String holdingA, String holdingB, String holdingC, String cash, BigDecimal liabilities) {
    var positions =
        List.of(
            new PositionSnapshot("IE00A", new BigDecimal(holdingA)),
            new PositionSnapshot("IE00B", new BigDecimal(holdingB)),
            new PositionSnapshot("IE00C", new BigDecimal(holdingC)));
    BigDecimal gross =
        positions.stream()
            .map(PositionSnapshot::marketValue)
            .reduce(new BigDecimal(cash), BigDecimal::add);
    return builder(positions, gross, new BigDecimal(cash).subtract(liabilities), liabilities)
        .build();
  }

  private static FundTransactionInput withFastSell(
      FundTransactionInput input, Set<String> fastSellIsins) {
    return builder(
            input.positions(), input.grossPortfolioValue(), input.freeCash(), input.liabilities())
        .fastSellIsins(fastSellIsins)
        .build();
  }

  private static FundTransactionInput.FundTransactionInputBuilder builder(
      List<PositionSnapshot> positions,
      BigDecimal gross,
      BigDecimal freeCash,
      BigDecimal liabilities) {
    return FundTransactionInput.builder()
        .fund(TUV100)
        .positions(positions)
        .modelWeights(MODEL)
        .grossPortfolioValue(gross)
        .cashBuffer(ZERO)
        .liabilities(liabilities)
        .freeCash(freeCash)
        .minTransactionThreshold(new BigDecimal("5000"));
  }

  private static Map<String, BigDecimal> tradeAmounts(FundCalculationResult result) {
    return result.trades().stream()
        .collect(
            toMap(
                TradeCalculation::isin,
                trade -> trade.tradeAmount().setScale(2, HALF_UP),
                (a, b) -> b,
                LinkedHashMap::new));
  }

  private static BigDecimal netTraded(FundCalculationResult result) {
    return result.trades().stream()
        .map(TradeCalculation::tradeAmount)
        .reduce(ZERO, BigDecimal::add);
  }
}
