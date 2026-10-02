package ee.tuleva.onboarding.investment.cashbuffer;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.FLOOR;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.util.List;

final class Percentile {

  private Percentile() {}

  static BigDecimal interpolatedBetweenClosestRanks(List<BigDecimal> values, BigDecimal fraction) {
    if (values.isEmpty() || fraction.compareTo(ZERO) < 0 || fraction.compareTo(ONE) > 0) {
      throw new IllegalArgumentException(
          "Percentile needs values and a fraction in [0, 1]: values="
              + values.size()
              + ", fraction="
              + fraction);
    }
    var sorted = values.stream().sorted().toList();
    var rank = fraction.multiply(BigDecimal.valueOf(sorted.size() - 1L));
    var lowerRank = rank.setScale(0, FLOOR).intValueExact();
    var below = sorted.get(lowerRank);
    var above = sorted.get(Math.min(lowerRank + 1, sorted.size() - 1));
    var weight = rank.subtract(BigDecimal.valueOf(lowerRank));
    return below.add(above.subtract(below).multiply(weight)).setScale(2, HALF_UP);
  }
}
