package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckResult.ReportPresence.IN_REPORT;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckResult.ReportPresence.MISSING_FROM_REPORT;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.*;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.FUND_IN_REPORT;
import static java.util.stream.Collectors.toCollection;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record HealthCheckResult(
    TulevaFund fund,
    LocalDate checkDate,
    List<HealthCheckFinding> findings,
    ReportPresence reportPresence) {

  public enum ReportPresence {
    IN_REPORT,
    MISSING_FROM_REPORT
  }

  public HealthCheckResult(
      TulevaFund fund, LocalDate checkDate, List<HealthCheckFinding> findings) {
    this(fund, checkDate, findings, IN_REPORT);
  }

  static HealthCheckResult missingFromReport(LocalDate checkDate, HealthCheckFinding finding) {
    return new HealthCheckResult(finding.fund(), checkDate, List.of(finding), MISSING_FROM_REPORT);
  }

  List<HealthCheckType> checkTypes() {
    return reportPresence == MISSING_FROM_REPORT
        ? List.of(FUND_IN_REPORT)
        : List.of(HealthCheckType.values());
  }

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

  public String importStatus() {
    if (reportPresence == MISSING_FROM_REPORT) {
      return "not in the report";
    }
    if (hasFails()) {
      return "blocked";
    }
    if (hasWarnings()) {
      return "imported with warnings";
    }
    if (hasIssues()) {
      return "imported, a check could not run";
    }
    return "imported";
  }
}
