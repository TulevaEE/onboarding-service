package ee.tuleva.onboarding.investment.check.fee;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

record FeeCheckFinding(
    TulevaFund fund,
    FeeCheckType checkType,
    FeeCheckScope scope,
    FeeCheckSeverity severity,
    String message,
    @Nullable BigDecimal deviationAmount,
    Map<String, Object> details) {

  static FeeCheckFinding pass(TulevaFund fund, FeeCheckType checkType, FeeCheckScope scope) {
    return new FeeCheckFinding(fund, checkType, scope, FeeCheckSeverity.PASS, "", null, Map.of());
  }

  // Written to the event row and compared against that row on the next run, so the two have to be
  // the same arithmetic. Computed separately they drifted: one summed absolute values from ZERO and
  // the other returned null for no deviation, so every repeat run looked like a change.
  static BigDecimal totalDeviation(List<FeeCheckFinding> findings) {
    return findings.stream()
        .map(FeeCheckFinding::deviationAmount)
        .filter(Objects::nonNull)
        .map(BigDecimal::abs)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
