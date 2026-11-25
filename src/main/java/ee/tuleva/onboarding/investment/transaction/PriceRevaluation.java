package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class PriceRevaluation {

  private static final int CENTS = 2;

  record Revalued(List<PositionSnapshot> positions, List<PriceValuation> valuations) {}

  private final Map<String, RevaluationPrice> prices;
  private final Map<String, BigDecimal> valuesWithoutUnits;

  private PriceRevaluation(
      Map<String, RevaluationPrice> prices, Map<String, BigDecimal> valuesWithoutUnits) {
    this.prices = prices;
    this.valuesWithoutUnits = valuesWithoutUnits;
  }

  static Revalued revalue(
      List<PositionSnapshot> positions,
      Map<String, RevaluationPrice> prices,
      Map<String, BigDecimal> valuesWithoutUnits) {
    if (prices.isEmpty()) {
      return new Revalued(positions, List.of());
    }
    PriceRevaluation revaluation = new PriceRevaluation(prices, valuesWithoutUnits);
    List<PositionSnapshot> revalued = positions.stream().map(revaluation::revalued).toList();
    List<PriceValuation> valuations =
        positions.stream()
            .map(PositionSnapshot::isin)
            .filter(Objects::nonNull)
            .filter(prices::containsKey)
            .distinct()
            .map(isin -> revaluation.valuation(isin, positions, revalued))
            .toList();
    return new Revalued(revalued, valuations);
  }

  private PositionSnapshot revalued(PositionSnapshot position) {
    String isin = position.isin();
    RevaluationPrice price = isin == null ? null : prices.get(isin);
    BigDecimal units = position.quantity();
    if (isin == null || price == null || units == null) {
      return position;
    }
    BigDecimal value =
        units
            .multiply(price.value())
            .add(valuesWithoutUnits.getOrDefault(isin, ZERO))
            .setScale(CENTS, HALF_UP);
    return new PositionSnapshot(isin, value, units, price.value());
  }

  private PriceValuation valuation(
      String isin, List<PositionSnapshot> positions, List<PositionSnapshot> revalued) {
    BigDecimal units =
        positions.stream()
            .filter(position -> isin.equals(position.isin()))
            .map(PositionSnapshot::quantity)
            .filter(Objects::nonNull)
            .reduce(BigDecimal::add)
            .orElse(null);
    RevaluationPrice price = Objects.requireNonNull(prices.get(isin));
    return new PriceValuation(
        isin,
        price.kind(),
        units,
        valueOf(isin, positions),
        price.value(),
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
