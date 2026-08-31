package ee.tuleva.onboarding.savings.fund.nav;

import java.math.BigDecimal;

public record NavTrackingDifference(BigDecimal trackingDifference, BigDecimal limit) {

  public boolean breachesLimit() {
    return trackingDifference.abs().compareTo(limit) >= 0;
  }
}
