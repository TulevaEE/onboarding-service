package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckScope.MANAGEMENT;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.FAIL;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.NOT_RUN;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.fee.FeeCheckType.FEE_BASE_COMPLETENESS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeeCheckResultTest {

  private static final LocalDate CHECK_DATE = LocalDate.of(2026, 6, 3);

  @Test
  void noFindingsIsNotAFail() {
    assertThat(result(List.of()).hasFails()).isFalse();
  }

  // The checks ship alert-only; hasFails is what a later escalation to a blocking gate reads.
  @Test
  void aWarningIsNotAFail() {
    assertThat(result(List.of(finding(WARNING))).hasFails()).isFalse();
  }

  @Test
  void aCheckThatCouldNotRunIsNotAFail() {
    assertThat(result(List.of(finding(NOT_RUN))).hasFails()).isFalse();
  }

  @Test
  void oneFailAmongWarningsIsAFail() {
    assertThat(result(List.of(finding(WARNING), finding(FAIL))).hasFails()).isTrue();
  }

  private static FeeCheckResult result(List<FeeCheckFinding> findings) {
    return new FeeCheckResult(TUK75, CHECK_DATE, null, findings);
  }

  private static FeeCheckFinding finding(FeeCheckSeverity severity) {
    return new FeeCheckFinding(
        TUK75, FEE_BASE_COMPLETENESS, MANAGEMENT, severity, "message", null, Map.of());
  }
}
