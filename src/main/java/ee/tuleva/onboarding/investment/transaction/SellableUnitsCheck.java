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
  private static final BigDecimal POSITION_MARKET_VALUE_IS_ROUNDED_TO_THE_CENT =
      new BigDecimal("0.01");

  private final Map<String, SellableUnits> sellableUnits;
  private final List<InputGateFailure> unsellable = new ArrayList<>();

  SellableUnitsCheck(Map<String, SellableUnits> sellableUnits) {
    this.sellableUnits = sellableUnits;
  }

  OrderQuantity fit(TransactionType transactionType, String isin, OrderQuantity sized) {
    BigDecimal quantity = sized.quantity();
    TradePrepPrice price = sized.price();
    BigDecimal priceValue = price == null ? null : price.value();
    if (transactionType != TransactionType.SELL || quantity == null || priceValue == null) {
      return sized;
    }
    SellableUnits sellable = sellableUnits.getOrDefault(isin, NOT_IN_THE_REPORT);
    BigDecimal excess = quantity.subtract(sellable.allowed());
    if (excess.signum() <= 0) {
      return sized;
    }
    if (excess.multiply(priceValue).compareTo(POSITION_MARKET_VALUE_IS_ROUNDED_TO_THE_CENT) <= 0) {
      return new OrderQuantity(sellable.allowed(), sized.stalePriceComment(), price);
    }
    unsellable.add(
        new SaleBeyondSellableUnits(
            isin,
            quantity,
            sellable.held(),
            sellable.pendingSells(),
            sellable.allowed(),
            priceValue));
    return sized;
  }

  List<InputGateFailure> unsellable() {
    return List.copyOf(unsellable);
  }
}
