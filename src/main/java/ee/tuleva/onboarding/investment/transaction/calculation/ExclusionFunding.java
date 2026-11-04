package ee.tuleva.onboarding.investment.transaction.calculation;

import static ee.tuleva.onboarding.investment.transaction.TransactionMode.BUY;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.transaction.FundTransactionInput;
import ee.tuleva.onboarding.investment.transaction.TradeCalculation;
import ee.tuleva.onboarding.investment.transaction.TransactionMode;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class ExclusionFunding {

  private ExclusionFunding() {}

  static void requireFunded(
      FundTransactionInput input, TransactionMode mode, List<TradeCalculation> trades) {
    if (input.excludedPositions().isEmpty() || mode == BUY) {
      return;
    }
    BigDecimal netTraded =
        trades.stream().map(TradeCalculation::tradeAmount).reduce(ZERO, BigDecimal::add);
    BigDecimal unfunded = netTraded.subtract(input.freeCash());
    if (unfunded.compareTo(input.minTransactionThreshold()) > 0) {
      throw new IllegalStateException(
          ("Excluded instruments leave the fund's cash needs unfunded: fund=%s, mode=%s,"
                  + " freeCash=%s, netTraded=%s, unfunded=%s, minTransactionThreshold=%s,"
                  + " excludedMarketValue=%s")
              .formatted(
                  input.fund().name(),
                  mode.name(),
                  input.freeCash().toPlainString(),
                  netTraded.toPlainString(),
                  unfunded.toPlainString(),
                  input.minTransactionThreshold().toPlainString(),
                  input.excludedMarketValue().toPlainString()));
    }
  }
}
