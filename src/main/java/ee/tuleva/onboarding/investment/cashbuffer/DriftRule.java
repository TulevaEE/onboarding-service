package ee.tuleva.onboarding.investment.cashbuffer;

import java.math.BigDecimal;

record DriftRule(BigDecimal threshold, int sustainRuns) {

  Drift judge(BigDecimal divergence, int previousConsecutiveRuns) {
    var drifted = divergence.abs().compareTo(threshold) > 0;
    return new Drift(
        divergence, threshold, drifted, drifted ? previousConsecutiveRuns + 1 : 0, sustainRuns);
  }
}
