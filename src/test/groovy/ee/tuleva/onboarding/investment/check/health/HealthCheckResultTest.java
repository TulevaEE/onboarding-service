package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.*;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.COMPLETENESS;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.ISIN_MATCH;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HealthCheckResultTest {

  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 4, 15);

  @Test
  void noFindingsReportsNoIssues() {
    var result = new HealthCheckResult(TUK75, CHECK_DATE, List.of());

    assertThat(result.hasIssues()).isFalse();
    assertThat(result.hasFails()).isFalse();
    assertThat(result.hasWarnings()).isFalse();
  }

  @Test
  void warningFindingReportsWarningButNotFail() {
    var finding = new HealthCheckFinding(TUK75, COMPLETENESS, WARNING, "missing CASH");
    var result = new HealthCheckResult(TUK75, CHECK_DATE, List.of(finding));

    assertThat(result.hasIssues()).isTrue();
    assertThat(result.hasWarnings()).isTrue();
    assertThat(result.hasFails()).isFalse();
  }

  @Test
  void failFindingReportsFail() {
    var finding = new HealthCheckFinding(TUK75, ISIN_MATCH, FAIL, "unknown ISIN");
    var result = new HealthCheckResult(TUK75, CHECK_DATE, List.of(finding));

    assertThat(result.hasIssues()).isTrue();
    assertThat(result.hasFails()).isTrue();
  }

  @Test
  void passFindingReportsNoIssues() {
    var finding = new HealthCheckFinding(TUK75, COMPLETENESS, PASS, "all good");
    var result = new HealthCheckResult(TUK75, CHECK_DATE, List.of(finding));

    assertThat(result.hasIssues()).isFalse();
    assertThat(result.hasFails()).isFalse();
    assertThat(result.hasWarnings()).isFalse();
  }
}
