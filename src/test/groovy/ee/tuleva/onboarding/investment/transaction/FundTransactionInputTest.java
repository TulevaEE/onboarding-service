package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FundTransactionInputTest {

  private static final PositionSnapshot HOLDING_A =
      new PositionSnapshot("IE00A", new BigDecimal("540000"));
  private static final PositionSnapshot HOLDING_B =
      new PositionSnapshot("IE00B", new BigDecimal("270000"));
  private static final PositionSnapshot HOLDING_C =
      new PositionSnapshot("IE00C", new BigDecimal("90000"));

  @Test
  void excluding_takesTheInstrumentOutOfTheTradableSetAndSpreadsItsWeightOverTheOthers() {
    var input =
        input()
            .positions(List.of(HOLDING_A, HOLDING_B, HOLDING_C))
            .modelWeights(modelWeights("0.60", "0.30", "0.10"))
            .build();

    var excluded = input.excluding(Set.of("IE00C"));

    assertThat(excluded)
        .isEqualTo(
            input()
                .positions(List.of(HOLDING_A, HOLDING_B))
                .modelWeights(
                    List.of(
                        new ModelWeight("IE00A", new BigDecimal("0.6666666667")),
                        new ModelWeight("IE00B", new BigDecimal("0.3333333333"))))
                .excludedPositions(List.of(HOLDING_C))
                .build());
  }

  @Test
  void excluding_aModelInstrumentTheFundDoesNotHold_spreadsItsWeightAndRecordsItWithNoHolding() {
    var input =
        input()
            .positions(List.of(HOLDING_A, HOLDING_B))
            .modelWeights(modelWeights("0.60", "0.30", "0.10"))
            .build();

    var excluded = input.excluding(Set.of("IE00C"));

    assertThat(excluded.positions()).containsExactly(HOLDING_A, HOLDING_B);
    assertThat(excluded.modelWeights())
        .containsExactly(
            new ModelWeight("IE00A", new BigDecimal("0.6666666667")),
            new ModelWeight("IE00B", new BigDecimal("0.3333333333")));
    assertThat(excluded.excludedPositions()).containsExactly(new PositionSnapshot("IE00C", ZERO));
    assertThat(excluded.excludedMarketValue()).isEqualByComparingTo(ZERO);
  }

  @Test
  void excluding_twice_keepsTheHoldingsSetAsideTheFirstTime() {
    var input =
        input()
            .positions(List.of(HOLDING_A, HOLDING_B, HOLDING_C))
            .modelWeights(modelWeights("0.60", "0.30", "0.10"))
            .build();

    var excluded = input.excluding(Set.of("IE00C")).excluding(Set.of("IE00B"));

    assertThat(excluded.positions()).containsExactly(HOLDING_A);
    assertThat(excluded.excludedPositions()).containsExactly(HOLDING_C, HOLDING_B);
    assertThat(excluded.excludedMarketValue()).isEqualByComparingTo("360000");
  }

  @Test
  void excluding_aHoldingOfAFundWithoutModelWeights_setsItAsideAndLeavesTheWeightsEmpty() {
    var input = input().positions(List.of(HOLDING_A, HOLDING_C)).modelWeights(List.of()).build();

    var excluded = input.excluding(Set.of("IE00C"));

    assertThat(excluded.positions()).containsExactly(HOLDING_A);
    assertThat(excluded.excludedPositions()).containsExactly(HOLDING_C);
    assertThat(excluded.modelWeights()).isEmpty();
  }

  @Test
  void excluding_nothing_returnsTheInputUnchanged() {
    var input =
        input()
            .positions(List.of(HOLDING_A, HOLDING_B, HOLDING_C))
            .modelWeights(modelWeights("0.60", "0.30", "0.10"))
            .build();

    assertThat(input.excluding(Set.of())).isSameAs(input);
  }

  @Test
  void excluding_anInstrumentTheFundNeitherHoldsNorHasInItsModel_isRejected() {
    var input =
        input()
            .positions(List.of(HOLDING_A, HOLDING_B, HOLDING_C))
            .modelWeights(modelWeights("0.60", "0.30", "0.10"))
            .build();

    assertThatThrownBy(() -> input.excluding(Set.of("IE00C", "IE00X")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void excluding_anInstrumentWithANegativeModelWeight_isRejectedLikeTheEngineRejectsIt() {
    var input =
        input()
            .positions(List.of(HOLDING_A, HOLDING_B, HOLDING_C))
            .modelWeights(modelWeights("1.10", "0.00", "-0.10"))
            .build();

    assertThatThrownBy(() -> input.excluding(Set.of("IE00C")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void excluding_everyInstrumentWithAModelWeight_isRejected() {
    var input =
        input()
            .positions(List.of(HOLDING_A, HOLDING_B, HOLDING_C))
            .modelWeights(
                List.of(
                    new ModelWeight("IE00A", new BigDecimal("0.70")),
                    new ModelWeight("IE00B", new BigDecimal("0.30"))))
            .build();

    assertThatThrownBy(() -> input.excluding(Set.of("IE00A", "IE00B")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void excludedMarketValue_addsUpTheHoldingsSetAside() {
    var input =
        input()
            .positions(List.of(HOLDING_A, HOLDING_B, HOLDING_C))
            .modelWeights(modelWeights("0.60", "0.30", "0.10"))
            .build();

    assertThat(input.excluding(Set.of("IE00B", "IE00C")).excludedMarketValue())
        .isEqualByComparingTo("360000");
    assertThat(input.excludedMarketValue()).isEqualByComparingTo(ZERO);
  }

  private static List<ModelWeight> modelWeights(String a, String b, String c) {
    return List.of(
        new ModelWeight("IE00A", new BigDecimal(a)),
        new ModelWeight("IE00B", new BigDecimal(b)),
        new ModelWeight("IE00C", new BigDecimal(c)));
  }

  private static FundTransactionInput.FundTransactionInputBuilder input() {
    return FundTransactionInput.builder()
        .fund(TUV100)
        .grossPortfolioValue(new BigDecimal("1000000"))
        .cashBuffer(ZERO)
        .liabilities(ZERO)
        .freeCash(new BigDecimal("100000"))
        .minTransactionThreshold(new BigDecimal("5000"));
  }
}
