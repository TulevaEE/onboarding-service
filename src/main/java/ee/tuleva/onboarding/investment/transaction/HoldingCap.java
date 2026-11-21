package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.CalculationWarningType.SELL_CAPPED_AT_HOLDING;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class HoldingCap {

  private final TulevaFund fund;
  private final Map<String, SellableUnits> sellableUnits;
  private final List<CalculationWarning> warnings = new ArrayList<>();

  HoldingCap(TulevaFund fund, Map<String, SellableUnits> sellableUnits) {
    this.fund = fund;
    this.sellableUnits = sellableUnits;
  }

  OrderQuantity cap(TransactionType transactionType, String isin, OrderQuantity sized) {
    BigDecimal quantity = sized.quantity();
    TradePrepPrice price = sized.price();
    if (transactionType != TransactionType.SELL || quantity == null || price == null) {
      return sized;
    }
    return Optional.ofNullable(sellableUnits.get(isin))
        .filter(sellable -> quantity.compareTo(sellable.allowed()) > 0)
        .map(
            sellable -> {
              warnings.add(warning(isin, quantity, sellable, price));
              return new OrderQuantity(sellable.allowed(), sized.stalePriceComment(), price);
            })
        .orElse(sized);
  }

  List<CalculationWarning> warnings() {
    return List.copyOf(warnings);
  }

  private CalculationWarning warning(
      String isin, BigDecimal computed, SellableUnits sellable, TradePrepPrice price) {
    return new CalculationWarning(
        SELL_CAPPED_AT_HOLDING,
        ("⚠️ Sale sized above the units it may sell, capped: fund=%s, isin=%s,"
                + " computedQuantity=%s, heldQuantity=%s, pendingSellQuantity=%s,"
                + " allowedQuantity=%s, price=%s")
            .formatted(
                fund.name(),
                isin,
                computed.toPlainString(),
                sellable.held().toPlainString(),
                sellable.pendingSells().toPlainString(),
                sellable.allowed().toPlainString(),
                Objects.requireNonNull(price.value()).toPlainString()));
  }
}
