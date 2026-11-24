package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.SizedSale.Kind.FULL;
import static ee.tuleva.onboarding.investment.transaction.SizedSale.Kind.PARTIAL;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.FLOOR;
import static java.math.RoundingMode.HALF_UP;
import static java.util.stream.Collectors.toMap;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@Slf4j
@NullMarked
final class SaleSizing {

  private static final BigDecimal TRADE_AMOUNT_IS_ROUNDED_TO_CENTS = new BigDecimal("0.01");
  private static final int CENTS = 2;
  private static final SellableUnits NOT_IN_THE_REPORT = new SellableUnits(ZERO, ZERO);

  record SizedOrder(BigDecimal amount, OrderQuantity quantity) {}

  private final Map<String, SellableUnits> sellableUnits;
  private final Map<String, BigDecimal> valueLeft;
  private final Map<String, BigDecimal> unitsLeft = new HashMap<>();
  private final List<SizedSale> sizedSales = new ArrayList<>();

  SaleSizing(Map<String, SellableUnits> sellableUnits, List<PositionSnapshot> positions) {
    this.sellableUnits = sellableUnits;
    this.valueLeft =
        positions.stream()
            .filter(position -> position.isin() != null)
            .collect(
                toMap(
                    position -> Objects.requireNonNull(position.isin()),
                    PositionSnapshot::marketValue,
                    BigDecimal::add,
                    HashMap::new));
  }

  Optional<SizedOrder> size(
      TransactionType transactionType,
      InstrumentType instrumentType,
      String isin,
      BigDecimal amount,
      OrderQuantity sized) {
    BigDecimal quantity = sized.quantity();
    BigDecimal price = priceOf(sized);
    if (transactionType != TransactionType.SELL || quantity == null || price == null) {
      return Optional.of(new SizedOrder(amount, sized));
    }
    return sizeSale(instrumentType, isin, amount, sized, quantity, price);
  }

  private Optional<SizedOrder> sizeSale(
      InstrumentType instrumentType,
      String isin,
      BigDecimal amount,
      OrderQuantity sized,
      BigDecimal quantity,
      BigDecimal price) {
    SellableUnits sellable = sellableUnits.getOrDefault(isin, NOT_IN_THE_REPORT);
    SizedSale.Kind kind = sellsWhatIsLeft(isin, amount) ? FULL : PARTIAL;
    BigDecimal reportUnits = sellable.held();
    if (reportUnits == null) {
      sizedSales.add(
          new SizedSale(
              isin, null, sellable.pendingSells(), quantity, price, amount, amount, null));
      if (quantity.signum() == 0) {
        return Optional.empty();
      }
      return Optional.of(new SizedOrder(amount, unsizedIfEtf(instrumentType, isin, sized)));
    }
    BigDecimal left = unitsLeft.getOrDefault(isin, sellableAs(instrumentType, sellable.allowed()));
    BigDecimal units = kind == FULL ? left : quantity.min(left);
    unitsLeft.put(isin, left.subtract(units));
    if (kind == PARTIAL && units.compareTo(quantity) == 0 && units.signum() > 0) {
      return Optional.of(new SizedOrder(amount, sized));
    }
    BigDecimal orderAmount = units.multiply(price).setScale(CENTS, HALF_UP);
    sizedSales.add(
        new SizedSale(
            isin, reportUnits, sellable.pendingSells(), units, price, amount, orderAmount, kind));
    return units.signum() == 0
        ? Optional.empty()
        : Optional.of(
            new SizedOrder(
                orderAmount, new OrderQuantity(units, sized.stalePriceComment(), sized.price())));
  }

  private static BigDecimal sellableAs(InstrumentType instrumentType, BigDecimal allowed) {
    return instrumentType == ETF ? allowed.setScale(0, FLOOR) : allowed;
  }

  private OrderQuantity unsizedIfEtf(
      InstrumentType instrumentType, String isin, OrderQuantity sized) {
    if (instrumentType != ETF) {
      return sized;
    }
    log.warn(
        "ETF sale has no held units to size against, leaving its quantity unset: isin={}", isin);
    return new OrderQuantity(null, sized.stalePriceComment(), sized.price());
  }

  private boolean sellsWhatIsLeft(String isin, BigDecimal amount) {
    BigDecimal left = valueLeft.getOrDefault(isin, ZERO);
    valueLeft.put(isin, left.subtract(amount));
    return amount.compareTo(left.subtract(TRADE_AMOUNT_IS_ROUNDED_TO_CENTS)) >= 0;
  }

  private static @Nullable BigDecimal priceOf(OrderQuantity sized) {
    TradePrepPrice price = sized.price();
    return price == null ? null : price.value();
  }

  List<SizedSale> sizedSales() {
    return List.copyOf(sizedSales);
  }
}
