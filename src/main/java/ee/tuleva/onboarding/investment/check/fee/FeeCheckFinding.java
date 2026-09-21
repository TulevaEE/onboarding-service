package ee.tuleva.onboarding.investment.check.fee;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

record FeeCheckFinding(
    TulevaFund fund,
    FeeCheckType checkType,
    FeeCheckScope scope,
    FeeCheckSeverity severity,
    String message,
    @Nullable BigDecimal deviationAmount,
    List<String> identifiers,
    Map<String, Object> details) {

  static FeeCheckFinding pass(TulevaFund fund, FeeCheckType checkType, FeeCheckScope scope) {
    return new FeeCheckFinding(
        fund, checkType, scope, FeeCheckSeverity.PASS, "", null, List.of(), Map.of());
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

  // Says what the check is reporting rather than what it adds up to, so that a finding carrying no
  // amount - a settlement transaction count, a day that accrued nothing at all - is still a visible
  // change, and so that two runs over different windows can be compared item by item.
  static List<String> fingerprint(List<FeeCheckFinding> findings) {
    return findings.stream()
        .flatMap(FeeCheckFinding::severityTaggedIdentifiers)
        .distinct()
        .sorted()
        .toList();
  }

  private Stream<String> severityTaggedIdentifiers() {
    return identifiers.stream().map(identifier -> severity + " " + identifier);
  }
}
