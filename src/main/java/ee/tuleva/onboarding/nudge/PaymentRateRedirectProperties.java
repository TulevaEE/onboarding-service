package ee.tuleva.onboarding.nudge;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nudge.payment-rate-redirect")
record PaymentRateRedirectProperties(
    boolean enabled,
    @Nullable String seed,
    int holdoutPercent,
    BigDecimal salaryThreshold,
    LocalDate startDate) {

  PaymentRateRedirectProperties {
    if (holdoutPercent < 0 || holdoutPercent > 100) {
      throw new IllegalStateException(
          "Payment rate redirect misconfigured: property=nudge.payment-rate-redirect.holdout-percent,"
              + " value="
              + holdoutPercent);
    }
  }

  String activeSeed() {
    if (seed == null || seed.isBlank()) {
      throw new IllegalStateException(
          "Payment rate redirect misconfigured: property=nudge.payment-rate-redirect.seed,"
              + " value=empty");
    }
    return seed;
  }
}
