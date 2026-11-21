package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.investment.transaction.CommandOutcome.StoppedByInputGates;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record CalculatedOrders(
    List<TransactionOrder> orders,
    Map<String, @Nullable TradePrepPrice> priceResolutions,
    List<InputGateFailure> unsellable) {

  void requireSellable(TulevaFund fund, LocalDate asOfDate) {
    if (!unsellable.isEmpty()) {
      throw new TradePrepInputGateException(new StoppedByInputGates(fund, asOfDate, unsellable));
    }
  }
}
