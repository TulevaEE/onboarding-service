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

  static BigDecimal totalDeviation(List<FeeCheckFinding> findings) {
    return findings.stream()
        .map(FeeCheckFinding::deviationAmount)
        .filter(Objects::nonNull)
        .map(BigDecimal::abs)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  static List<String> fingerprint(List<FeeCheckFinding> findings) {
    return findings.stream()
        .flatMap(FeeCheckFinding::severityTaggedIdentifiers)
        .distinct()
        .sorted()
        .toList();
  }

  boolean carriesAnyOf(List<String> taggedIdentifiers) {
    return severityTaggedIdentifiers().anyMatch(taggedIdentifiers::contains);
  }

  private Stream<String> severityTaggedIdentifiers() {
    return identifiers.stream().map(identifier -> severity + " " + identifier);
  }
}
