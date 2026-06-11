package ee.tuleva.onboarding.investment.check.tracking;

import java.math.BigDecimal;

public record BenchmarkModelTrackingDifference(BigDecimal trackingDifference, BigDecimal limit) {

  public boolean breachesLimit() {
    return trackingDifference.abs().compareTo(limit) >= 0;
  }
}
