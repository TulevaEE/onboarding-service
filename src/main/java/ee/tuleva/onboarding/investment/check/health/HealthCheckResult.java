package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckResult.ReportPresence.IN_REPORT;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckResult.ReportPresence.MISSING_FROM_REPORT;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckResult.ReportPresence.NOT_READ_FROM_REPORT;
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
    MISSING_FROM_REPORT,
    NOT_READ_FROM_REPORT
  }

  public HealthCheckResult(
      TulevaFund fund, LocalDate checkDate, List<HealthCheckFinding> findings) {
    this(fund, checkDate, findings, IN_REPORT);
  }

  static HealthCheckResult missingFromReport(LocalDate checkDate, HealthCheckFinding finding) {
    return new HealthCheckResult(finding.fund(), checkDate, List.of(finding), MISSING_FROM_REPORT);
  }

  static HealthCheckResult notReadFromReport(LocalDate checkDate, HealthCheckFinding finding) {
    return new HealthCheckResult(finding.fund(), checkDate, List.of(finding), NOT_READ_FROM_REPORT);
  }

  List<HealthCheckType> checkTypes() {
    return reportPresence == IN_REPORT
        ? List.of(HealthCheckType.values())
        : List.of(FUND_IN_REPORT);
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
    return switch (reportPresence) {
      case MISSING_FROM_REPORT -> "not in the report";
      case NOT_READ_FROM_REPORT -> "not read from the report";
      case IN_REPORT -> importStatusOfAFundInTheReport();
    };
  }

  private String importStatusOfAFundInTheReport() {
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
