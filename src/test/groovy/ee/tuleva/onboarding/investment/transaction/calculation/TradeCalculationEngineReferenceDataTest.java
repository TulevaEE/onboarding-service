package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.REBALANCE;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import ee.tuleva.onboarding.investment.transaction.ModelWeight;
import ee.tuleva.onboarding.investment.transaction.PositionLimitSnapshot;
import ee.tuleva.onboarding.investment.transaction.PositionSnapshot;
import ee.tuleva.onboarding.investment.transaction.TradeCalculation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TradeCalculationEngineReferenceDataTest {

  private final TradeCalculationEngine engine = new TradeCalculationEngine();

  private static final BigDecimal GROSS_PORTFOLIO_VALUE = new BigDecimal("35490956.78");
  private static final BigDecimal CASH_BUFFER = new BigDecimal("150000");
  private static final BigDecimal LIABILITIES = new BigDecimal("13397.68");
  private static final BigDecimal FREE_CASH = new BigDecimal("352484.34");
  private static final BigDecimal MIN_TRANSACTION_THRESHOLD = new BigDecimal("50000");

  private static final List<PositionSnapshot> POSITIONS =
      List.of(
          new PositionSnapshot("IE00BMDBMY19", new BigDecimal("528888.44")),
          new PositionSnapshot("IE00BFG1TM61", new BigDecimal("3667635.87")),
          new PositionSnapshot("IE00BJZ2DC62", new BigDecimal("5336198.14")),
          new PositionSnapshot("LU0476289540", new BigDecimal("4029441.60")),
          new PositionSnapshot("IE000F60HVH9", new BigDecimal("117751.10")),
          new PositionSnapshot("IE000O58J820", new BigDecimal("3098865.44")),
          new PositionSnapshot("LU1291099718", new BigDecimal("13705241.96")),
          new PositionSnapshot("LU1291106356", new BigDecimal("2769960.52")),
          new PositionSnapshot("LU1291102447", new BigDecimal("1721091.69")));

  private static final List<ModelWeight> MODEL_WEIGHTS =
      List.of(
          new ModelWeight("IE00BMDBMY19", new BigDecimal("0.100")),
          new ModelWeight("IE00BFG1TM61", new BigDecimal("0.190")),
          new ModelWeight("IE00BJZ2DC62", new BigDecimal("0.174")),
          new ModelWeight("LU0476289540", new BigDecimal("0.016")),
          new ModelWeight("IE000F60HVH9", new BigDecimal("0.185")),
          new ModelWeight("IE000O58J820", new BigDecimal("0.154")),
          new ModelWeight("LU1291099718", new BigDecimal("0.125")),
          new ModelWeight("LU1291106356", new BigDecimal("0.019")),
          new ModelWeight("LU1291102447", new BigDecimal("0.037")));

  private static final Map<String, PositionLimitSnapshot> POSITION_LIMITS =
      Map.of(
          "IE00BMDBMY19",
              new PositionLimitSnapshot(new BigDecimal("0.1070"), new BigDecimal("0.1150")),
          "IE00BFG1TM61",
              new PositionLimitSnapshot(new BigDecimal("0.1965"), new BigDecimal("0.2000")),
          "IE00BJZ2DC62",
              new PositionLimitSnapshot(new BigDecimal("0.1862"), new BigDecimal("0.2000")),
          "LU0476289540",
              new PositionLimitSnapshot(new BigDecimal("0.0171"), new BigDecimal("0.0184")),
          "IE000F60HVH9",
              new PositionLimitSnapshot(new BigDecimal("0.1965"), new BigDecimal("0.2000")),
          "IE000O58J820",
              new PositionLimitSnapshot(new BigDecimal("0.1648"), new BigDecimal("0.1771")),
          "LU1291099718",
              new PositionLimitSnapshot(new BigDecimal("0.1338"), new BigDecimal("0.1437")),
          "LU1291106356",
              new PositionLimitSnapshot(new BigDecimal("0.0203"), new BigDecimal("0.0219")),
          "LU1291102447",
              new PositionLimitSnapshot(new BigDecimal("0.0396"), new BigDecimal("0.0426")));

  private FundTransactionInput buildInput() {
    return FundTransactionInput.builder()
        .fund(TKF100)
        .positions(POSITIONS)
        .modelWeights(MODEL_WEIGHTS)
        .grossPortfolioValue(GROSS_PORTFOLIO_VALUE)
        .cashBuffer(CASH_BUFFER)
        .liabilities(LIABILITIES)
        .freeCash(FREE_CASH)
        .minTransactionThreshold(MIN_TRANSACTION_THRESHOLD)
        .positionLimits(POSITION_LIMITS)
        .build();
  }

  @Test
  void tkf100RebalanceMode_producesExpectedTradeAmounts() {
    var input = buildInput();

    var result = engine.calculate(input, REBALANCE);

    Map<String, BigDecimal> tradesByIsin =
        result.trades().stream()
            .collect(toMap(TradeCalculation::isin, TradeCalculation::tradeAmount));

    BigDecimal tolerance = BigDecimal.ONE;
    assertThat(tradesByIsin.get("IE00BMDBMY19"))
        .isCloseTo(new BigDecimal("3003867"), within(tolerance));
    assertThat(tradesByIsin.get("IE00BFG1TM61"))
        .isCloseTo(new BigDecimal("3044600"), within(tolerance));
    assertThat(tradesByIsin.get("IE00BJZ2DC62"))
        .isCloseTo(new BigDecimal("810797"), within(tolerance));
    assertThat(tradesByIsin.get("LU0476289540"))
        .isCloseTo(new BigDecimal("-3464201"), within(tolerance));
    assertThat(tradesByIsin.get("IE000F60HVH9"))
        .isCloseTo(new BigDecimal("6417847"), within(tolerance));
    assertThat(tradesByIsin.get("IE000O58J820"))
        .isCloseTo(new BigDecimal("2341579"), within(tolerance));
    assertThat(tradesByIsin.get("LU1291099718"))
        .isCloseTo(new BigDecimal("-9289297"), within(tolerance));
    assertThat(tradesByIsin.get("LU1291106356"))
        .isCloseTo(new BigDecimal("-2098737"), within(tolerance));
    assertThat(tradesByIsin.get("LU1291102447"))
        .isCloseTo(new BigDecimal("-413972"), within(tolerance));
  }

  @Test
  void tkf100BuyMode_allocatesFreeCashToUnderweightPositions() {
    var input = buildInput();

    var result = engine.calculate(input, BUY);

    Map<String, BigDecimal> tradesByIsin =
        result.trades().stream()
            .collect(toMap(TradeCalculation::isin, TradeCalculation::tradeAmount));

    BigDecimal totalBuyAmount = tradesByIsin.values().stream().reduce(ZERO, BigDecimal::add);
    assertThat(totalBuyAmount).isCloseTo(FREE_CASH, within(BigDecimal.ONE));

    assertThat(tradesByIsin.values()).allMatch(amount -> amount.compareTo(ZERO) >= 0);

    assertThat(tradesByIsin.get("LU0476289540")).isEqualByComparingTo(ZERO);

    tradesByIsin.values().stream()
        .filter(amount -> amount.compareTo(ZERO) > 0)
        .forEach(
            amount ->
                assertThat(amount)
                    .isGreaterThanOrEqualTo(MIN_TRANSACTION_THRESHOLD.subtract(BigDecimal.ONE)));
  }
}
