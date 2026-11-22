package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class ManualPriceRevaluation {

  private static final int CENTS = 2;

  record Revalued(List<PositionSnapshot> positions, List<ManualPriceValuation> valuations) {}

  private ManualPriceRevaluation() {}

  static Revalued revalue(List<PositionSnapshot> positions, Map<String, BigDecimal> manualPrices) {
    if (manualPrices.isEmpty()) {
      return new Revalued(positions, List.of());
    }
    List<PositionSnapshot> revalued =
        positions.stream().map(position -> revalued(position, manualPrices)).toList();
    List<ManualPriceValuation> valuations =
        positions.stream()
            .map(PositionSnapshot::isin)
            .filter(Objects::nonNull)
            .filter(manualPrices::containsKey)
            .distinct()
            .map(
                isin ->
                    valuation(
                        isin, positions, revalued, Objects.requireNonNull(manualPrices.get(isin))))
            .toList();
    return new Revalued(revalued, valuations);
  }

  private static PositionSnapshot revalued(
      PositionSnapshot position, Map<String, BigDecimal> manualPrices) {
    String isin = position.isin();
    BigDecimal price = isin == null ? null : manualPrices.get(isin);
    BigDecimal units = position.quantity();
    if (price == null || units == null) {
      return position;
    }
    return new PositionSnapshot(isin, units.multiply(price).setScale(CENTS, HALF_UP), units, price);
  }

  private static ManualPriceValuation valuation(
      String isin,
      List<PositionSnapshot> positions,
      List<PositionSnapshot> revalued,
      BigDecimal manualPrice) {
    return new ManualPriceValuation(
        isin,
        positions.stream()
            .filter(position -> isin.equals(position.isin()))
            .map(PositionSnapshot::quantity)
            .filter(Objects::nonNull)
            .reduce(BigDecimal::add)
            .orElse(null),
        valueOf(isin, positions),
        manualPrice,
        valueOf(isin, revalued));
  }

  private static BigDecimal valueOf(String isin, List<PositionSnapshot> positions) {
    return positions.stream()
        .filter(position -> isin.equals(position.isin()))
        .map(PositionSnapshot::marketValue)
        .reduce(ZERO, BigDecimal::add);
  }
}
