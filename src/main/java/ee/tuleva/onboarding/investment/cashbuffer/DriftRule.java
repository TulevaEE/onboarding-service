package ee.tuleva.onboarding.investment.cashbuffer;

import java.math.BigDecimal;
import java.util.Optional;

record DriftRule(BigDecimal threshold, int sustainRuns) {

  Drift judge(BigDecimal divergence, Optional<Drift> previousAgainstTheSameLimit) {
    var drifted = divergence.abs().compareTo(threshold) > 0;
    var runsBefore =
        previousAgainstTheSameLimit.map(previous -> previous.runsContinuedBy(divergence)).orElse(0);
    return new Drift(divergence, threshold, drifted, drifted ? runsBefore + 1 : 0, sustainRuns);
  }
}
