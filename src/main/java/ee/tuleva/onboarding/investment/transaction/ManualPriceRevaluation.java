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

  private final Map<String, BigDecimal> manualPrices;
  private final Map<String, BigDecimal> valuesWithoutUnits;

  private ManualPriceRevaluation(
      Map<String, BigDecimal> manualPrices, Map<String, BigDecimal> valuesWithoutUnits) {
    this.manualPrices = manualPrices;
    this.valuesWithoutUnits = valuesWithoutUnits;
  }

  static Revalued revalue(
      List<PositionSnapshot> positions,
      Map<String, BigDecimal> manualPrices,
      Map<String, BigDecimal> valuesWithoutUnits) {
    if (manualPrices.isEmpty()) {
      return new Revalued(positions, List.of());
    }
    ManualPriceRevaluation revaluation =
        new ManualPriceRevaluation(manualPrices, valuesWithoutUnits);
    List<PositionSnapshot> revalued = positions.stream().map(revaluation::revalued).toList();
    List<ManualPriceValuation> valuations =
        positions.stream()
            .map(PositionSnapshot::isin)
            .filter(Objects::nonNull)
            .filter(manualPrices::containsKey)
            .distinct()
            .map(isin -> revaluation.valuation(isin, positions, revalued))
            .toList();
    return new Revalued(revalued, valuations);
  }

  private PositionSnapshot revalued(PositionSnapshot position) {
    String isin = position.isin();
    BigDecimal price = isin == null ? null : manualPrices.get(isin);
    BigDecimal units = position.quantity();
    if (isin == null || price == null || units == null) {
      return position;
    }
    BigDecimal value =
        units
            .multiply(price)
            .add(valuesWithoutUnits.getOrDefault(isin, ZERO))
            .setScale(CENTS, HALF_UP);
    return new PositionSnapshot(isin, value, units, price);
  }

  private ManualPriceValuation valuation(
      String isin, List<PositionSnapshot> positions, List<PositionSnapshot> revalued) {
    BigDecimal units =
        positions.stream()
            .filter(position -> isin.equals(position.isin()))
            .map(PositionSnapshot::quantity)
            .filter(Objects::nonNull)
            .reduce(BigDecimal::add)
            .orElse(null);
    return new ManualPriceValuation(
        isin,
        units,
        valueOf(isin, positions),
        Objects.requireNonNull(manualPrices.get(isin)),
        units == null ? ZERO : valuesWithoutUnits.getOrDefault(isin, ZERO),
        valueOf(isin, revalued));
  }

  private static BigDecimal valueOf(String isin, List<PositionSnapshot> positions) {
    return positions.stream()
        .filter(position -> isin.equals(position.isin()))
        .map(PositionSnapshot::marketValue)
        .reduce(ZERO, BigDecimal::add);
  }
}
