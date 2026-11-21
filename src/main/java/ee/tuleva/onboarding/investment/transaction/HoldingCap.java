package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.CalculationWarningType.SELL_CAPPED_AT_HOLDING;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class HoldingCap {

  private final TulevaFund fund;
  private final List<PositionSnapshot> positions;
  private final List<CalculationWarning> warnings = new ArrayList<>();

  HoldingCap(TulevaFund fund, List<PositionSnapshot> positions) {
    this.fund = fund;
    this.positions = positions;
  }

  OrderQuantity cap(TransactionType transactionType, String isin, OrderQuantity sized) {
    BigDecimal quantity = sized.quantity();
    TradePrepPrice price = sized.price();
    if (transactionType != TransactionType.SELL || quantity == null || price == null) {
      return sized;
    }
    return positions.stream()
        .filter(position -> isin.equals(position.isin()))
        .map(PositionSnapshot::quantity)
        .filter(Objects::nonNull)
        .findFirst()
        .filter(held -> quantity.compareTo(held) > 0)
        .map(
            held -> {
              warnings.add(warning(isin, quantity, held, price));
              return new OrderQuantity(held, sized.stalePriceComment(), price);
            })
        .orElse(sized);
  }

  List<CalculationWarning> warnings() {
    return List.copyOf(warnings);
  }

  private CalculationWarning warning(
      String isin, BigDecimal computed, BigDecimal held, TradePrepPrice price) {
    return new CalculationWarning(
        SELL_CAPPED_AT_HOLDING,
        ("⚠️ Sale sized above the units held, capped at the holding: fund=%s, isin=%s,"
                + " computedQuantity=%s, heldQuantity=%s, price=%s")
            .formatted(
                fund.name(),
                isin,
                computed.toPlainString(),
                held.toPlainString(),
                Objects.requireNonNull(price.value()).toPlainString()));
  }
}
