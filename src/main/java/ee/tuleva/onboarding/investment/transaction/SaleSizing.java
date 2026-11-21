package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.SizedSale.Kind.FULL;
import static ee.tuleva.onboarding.investment.transaction.SizedSale.Kind.PARTIAL;
import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
final class SaleSizing {

  private static final SellableUnits NOT_IN_THE_REPORT = new SellableUnits(ZERO, ZERO);

  private final Map<String, SellableUnits> sellableUnits;
  private final List<PositionSnapshot> positions;
  private final List<SizedSale> sizedSales = new ArrayList<>();

  SaleSizing(Map<String, SellableUnits> sellableUnits, List<PositionSnapshot> positions) {
    this.sellableUnits = sellableUnits;
    this.positions = positions;
  }

  Optional<OrderQuantity> size(
      TransactionType transactionType, String isin, BigDecimal amount, OrderQuantity sized) {
    BigDecimal quantity = sized.quantity();
    BigDecimal price = priceOf(sized);
    if (transactionType != TransactionType.SELL || quantity == null || price == null) {
      return Optional.of(sized);
    }
    return sizeSale(isin, amount, sized, quantity, price);
  }

  private Optional<OrderQuantity> sizeSale(
      String isin, BigDecimal amount, OrderQuantity sized, BigDecimal quantity, BigDecimal price) {
    SellableUnits sellable = sellableUnits.getOrDefault(isin, NOT_IN_THE_REPORT);
    SizedSale.Kind kind = amount.compareTo(marketValueOf(isin)) >= 0 ? FULL : PARTIAL;
    BigDecimal reportUnits = sellable.held();
    if (reportUnits == null) {
      sizedSales.add(new SizedSale(isin, null, sellable.pendingSells(), quantity, price, kind));
      return Optional.of(sized);
    }
    BigDecimal units = kind == FULL ? sellable.allowed() : quantity.min(sellable.allowed());
    if (kind == PARTIAL && units.compareTo(quantity) == 0) {
      return Optional.of(sized);
    }
    sizedSales.add(new SizedSale(isin, reportUnits, sellable.pendingSells(), units, price, kind));
    return units.signum() == 0
        ? Optional.empty()
        : Optional.of(new OrderQuantity(units, sized.stalePriceComment(), sized.price()));
  }

  private static @Nullable BigDecimal priceOf(OrderQuantity sized) {
    TradePrepPrice price = sized.price();
    return price == null ? null : price.value();
  }

  List<SizedSale> sizedSales() {
    return List.copyOf(sizedSales);
  }

  private BigDecimal marketValueOf(String isin) {
    return positions.stream()
        .filter(position -> isin.equals(position.isin()))
        .map(PositionSnapshot::marketValue)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(ZERO);
  }
}
