package ee.tuleva.onboarding.investment.cashbuffer;

import java.math.BigDecimal;

record Drift(
    BigDecimal divergence,
    BigDecimal threshold,
    boolean drifted,
    int consecutiveRuns,
    int sustainRuns) {

  boolean sustained() {
    return drifted && consecutiveRuns >= sustainRuns;
  }
}
