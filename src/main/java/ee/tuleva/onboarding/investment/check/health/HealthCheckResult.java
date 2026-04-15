package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.*;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;

public record HealthCheckResult(
    TulevaFund fund, LocalDate checkDate, List<HealthCheckFinding> findings) {

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
