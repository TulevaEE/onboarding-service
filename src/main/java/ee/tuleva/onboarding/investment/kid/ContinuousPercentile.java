package ee.tuleva.onboarding.investment.kid;

import java.util.List;

final class ContinuousPercentile {

  private ContinuousPercentile() {}

  static double of(List<Double> values, double fraction) {
    var sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    var position = fraction * (sorted.length - 1);
    var below = (int) Math.floor(position);
    var above = (int) Math.ceil(position);
    return sorted[below] + (position - below) * (sorted[above] - sorted[below]);
  }
}
