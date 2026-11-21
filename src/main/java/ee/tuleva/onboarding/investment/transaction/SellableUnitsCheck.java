package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.transaction.InputGateFailure.SaleBeyondSellableUnits;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class SellableUnitsCheck {

  private static final SellableUnits NOT_IN_THE_REPORT = new SellableUnits(ZERO, ZERO);

  private final Map<String, SellableUnits> sellableUnits;
  private final List<InputGateFailure> unsellable = new ArrayList<>();

  SellableUnitsCheck(Map<String, SellableUnits> sellableUnits) {
    this.sellableUnits = sellableUnits;
  }

  void check(TransactionType transactionType, String isin, OrderQuantity sized) {
    BigDecimal quantity = sized.quantity();
    TradePrepPrice price = sized.price();
    if (transactionType != TransactionType.SELL || quantity == null || price == null) {
      return;
    }
    BigDecimal priceValue = price.value();
    SellableUnits sellable = sellableUnits.getOrDefault(isin, NOT_IN_THE_REPORT);
    if (priceValue != null && quantity.compareTo(sellable.allowed()) > 0) {
      unsellable.add(
          new SaleBeyondSellableUnits(
              isin,
              quantity,
              sellable.held(),
              sellable.pendingSells(),
              sellable.allowed(),
              priceValue));
    }
  }

  List<InputGateFailure> unsellable() {
    return List.copyOf(unsellable);
  }
}
