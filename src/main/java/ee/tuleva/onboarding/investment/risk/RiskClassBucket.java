package ee.tuleva.onboarding.investment.risk;

import java.math.BigDecimal;
import java.util.List;

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

  private static int classify(List<Bucket> buckets, BigDecimal value) {
    return buckets.stream()
        .filter(bucket -> value.compareTo(bucket.upperBoundExclusive()) < 0)
        .findFirst()
        .map(Bucket::riskClass)
        .orElse(HIGHEST_CLASS);
  }

  private record Bucket(BigDecimal upperBoundExclusive, int riskClass) {}
}
