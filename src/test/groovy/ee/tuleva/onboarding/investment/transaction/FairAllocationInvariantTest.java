package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.BUY;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.calculation.TradeCalculationEngine;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Fair-allocation invariant for the trade calculation engine (R6).
 *
 * <p><b>Regulatory basis:</b> Directive 2010/43/EU art 28 (order aggregation and allocation)
 * obliges fair allocation only where orders for different clients/funds are aggregated into a
 * single pool. Tuleva's calculation engine computes each fund independently from that fund's own
 * inputs — there is no cross-fund aggregation pool — so the art 28 obligation is not triggered by
 * construction.
 *
 * <p>This test pins that invariant so a future change introducing cross-fund pooling fails here:
 *
 * <ul>
 *   <li>{@link TradeCalculationEngine#calculate} consumes a single {@link FundTransactionInput} for
 *       one fund and yields a {@link FundCalculationResult} for exactly that fund.
 *   <li>The trades produced reference only the input fund's own positions — feeding two funds whose
 *       ISIN universes overlap produces two independent results, neither aware of the other's free
 *       cash, positions, or limits.
 *   <li>A {@link TransactionBatch} carries exactly one {@code fund} (no collection of funds).
 * </ul>
 */
class FairAllocationInvariantTest {

  private final TradeCalculationEngine engine = new TradeCalculationEngine();

  @Test
  void calculationResultIsScopedToTheSingleInputFund() {
    var result = engine.calculate(singleFundInput(TUV100, "IE00A"), BUY);

    assertThat(result.fund()).isEqualTo(TUV100);
    assertThat(result.input().fund()).isEqualTo(TUV100);
  }

  @Test
  void twoFundsSharingAnIsinAreComputedIndependentlyWithNoCrossFundPooling() {
    String sharedIsin = "IE00SHARED";

    var firstFundInput = singleFundInput(TUV100, sharedIsin);
    var secondFundInput = singleFundInput(TKF100, sharedIsin);

    var firstResult = engine.calculate(firstFundInput, BUY);
    var secondResult = engine.calculate(secondFundInput, BUY);

    assertThat(firstResult.fund()).isEqualTo(TUV100);
    assertThat(secondResult.fund()).isEqualTo(TKF100);

    assertThat(tradedIsins(firstResult)).containsExactly(sharedIsin);
    assertThat(tradedIsins(secondResult)).containsExactly(sharedIsin);

    // No pooling: each fund allocates only its own free cash, independent of the other fund.
    assertThat(totalTraded(firstResult)).isEqualByComparingTo(firstFundInput.freeCash());
    assertThat(totalTraded(secondResult)).isEqualByComparingTo(secondFundInput.freeCash());
  }

  @Test
  void everyOrderAndItsBatchCarryExactlyOneAndTheSameFund() {
    var batch = TransactionBatch.builder().fund(TUV100).createdBy("system").build();

    var order = TransactionOrder.builder().batch(batch).fund(batch.getFund()).build();

    assertThat(order.getFund()).isEqualTo(batch.getFund());
    assertThat(order.getBatch().getFund()).isEqualTo(TUV100);
  }

  private static FundTransactionInput singleFundInput(
      ee.tuleva.onboarding.fund.TulevaFund fund, String isin) {
    return FundTransactionInput.builder()
        .fund(fund)
        .positions(List.of(new PositionSnapshot(isin, new BigDecimal("500000"))))
        .modelWeights(List.of(new ModelWeight(isin, new BigDecimal("1.00"))))
        .grossPortfolioValue(new BigDecimal("1000000"))
        .cashBuffer(new BigDecimal("50000"))
        .liabilities(ZERO)
        .freeCash(new BigDecimal("100000"))
        .minTransactionThreshold(new BigDecimal("5000"))
        .positionLimits(Map.of())
        .fastSellIsins(Set.of())
        .build();
  }

  private static List<String> tradedIsins(FundCalculationResult result) {
    return result.trades().stream()
        .filter(trade -> trade.tradeAmount().compareTo(ZERO) != 0)
        .map(TradeCalculation::isin)
        .toList();
  }

  private static BigDecimal totalTraded(FundCalculationResult result) {
    return result.trades().stream()
        .map(TradeCalculation::tradeAmount)
        .reduce(ZERO, BigDecimal::add);
  }
}
