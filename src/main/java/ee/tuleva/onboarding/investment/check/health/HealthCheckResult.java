package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.*;
import static java.util.stream.Collectors.toCollection;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record HealthCheckResult(
    TulevaFund fund, LocalDate checkDate, List<HealthCheckFinding> findings) {

  public static Set<TulevaFund> blockedFunds(List<HealthCheckResult> results) {
    return results.stream()
        .filter(HealthCheckResult::hasFails)
        .map(HealthCheckResult::fund)
        .collect(toCollection(() -> EnumSet.noneOf(TulevaFund.class)));
  }

  public boolean hasFails() {
    return findings.stream().anyMatch(f -> f.severity() == FAIL);
  }

  public boolean hasWarnings() {
    return findings.stream().anyMatch(f -> f.severity() == WARNING);
  }

  public boolean hasIssues() {
    return findings.stream().anyMatch(f -> f.severity() != PASS);
  }
}
