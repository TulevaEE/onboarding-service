package ee.tuleva.onboarding.investment.risk;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class RiskClassBucket {

  private static final List<Bucket> MRM_BUCKETS =
      List.of(
          new Bucket(new BigDecimal("0.005"), 1),
          new Bucket(new BigDecimal("0.05"), 2),
          new Bucket(new BigDecimal("0.12"), 3),
          new Bucket(new BigDecimal("0.20"), 4),
          new Bucket(new BigDecimal("0.30"), 5),
          new Bucket(new BigDecimal("0.80"), 6));

  private static final List<Bucket> SRRI_BUCKETS =
      List.of(
          new Bucket(new BigDecimal("0.005"), 1),
          new Bucket(new BigDecimal("0.02"), 2),
          new Bucket(new BigDecimal("0.05"), 3),
          new Bucket(new BigDecimal("0.10"), 4),
          new Bucket(new BigDecimal("0.15"), 5),
          new Bucket(new BigDecimal("0.25"), 6));

  private static final int HIGHEST_CLASS = 7;

  private RiskClassBucket() {}

  static int mrmClass(BigDecimal varEquivalentVolatility) {
    return classify(MRM_BUCKETS, varEquivalentVolatility);
  }

  static int mrmClass(double varEquivalentVolatility) {
    return mrmClass(BigDecimal.valueOf(varEquivalentVolatility));
  }

  static int srriClass(BigDecimal annualisedVolatility) {
    return classify(SRRI_BUCKETS, annualisedVolatility);
  }

  static int srriClass(double annualisedVolatility) {
    return srriClass(BigDecimal.valueOf(annualisedVolatility));
  }

  static ClassRange range(RiskIndicatorType indicatorType, int riskClass) {
    var buckets = indicatorType == RiskIndicatorType.SRI ? MRM_BUCKETS : SRRI_BUCKETS;
    return new ClassRange(
        riskClass <= 1 ? null : buckets.get(riskClass - 2).upperBoundExclusive(),
        riskClass > buckets.size() ? null : buckets.get(riskClass - 1).upperBoundExclusive());
  }

  /**
   * How far the value sits from the nearer edge of its class. The open ends of classes 1 and 7 have
   * only one edge.
   */
  static @Nullable BigDecimal distanceToNearestBound(
      RiskIndicatorType indicatorType, int riskClass, BigDecimal value) {
    var range = range(indicatorType, riskClass);
    var toLower = range.lowerInclusive() == null ? null : value.subtract(range.lowerInclusive());
    var toUpper = range.upperExclusive() == null ? null : range.upperExclusive().subtract(value);
    if (toLower == null) {
      return toUpper;
    }
    if (toUpper == null) {
      return toLower;
    }
    return toLower.min(toUpper);
  }

  private static int classify(List<Bucket> buckets, BigDecimal value) {
    return buckets.stream()
        .filter(bucket -> value.compareTo(bucket.upperBoundExclusive()) < 0)
        .findFirst()
        .map(Bucket::riskClass)
        .orElse(HIGHEST_CLASS);
  }

  private record Bucket(BigDecimal upperBoundExclusive, int riskClass) {}

  record ClassRange(@Nullable BigDecimal lowerInclusive, @Nullable BigDecimal upperExclusive) {}
}
