package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class HealthCheckAlertTest {

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 4, 15);

  @Test
  void aLateMessageSaysWhenItsStateWasFoundInTallinnTimeOnItsFirstLine() {
    var alert =
        new HealthCheckAlert(
            1L,
            SEB,
            REPORT_DATE,
            "IMPORT BLOCKED: SEB 2026-04-15 — TUK75 not imported, source files need to be fixed\n"
                + "Funds: TUK75 blocked\n"
                + "\n🛑 [FAIL] ISIN_MATCH TUK75: TUK75: unknown ISIN",
            Instant.parse("2026-04-16T06:10:00Z"),
            0);

    assertThat(alert.lateMessage())
        .isEqualTo(
            "IMPORT BLOCKED: SEB 2026-04-15 — TUK75 not imported, source files need to be fixed"
                + " (found 2026-04-16 09:10, posted late)\n"
                + "Funds: TUK75 blocked\n"
                + "\n🛑 [FAIL] ISIN_MATCH TUK75: TUK75: unknown ISIN");
  }

  @Test
  void aOneLineMessageSaysWhenItsStateWasFoundAtItsEnd() {
    var alert =
        new HealthCheckAlert(
            1L,
            SEB,
            REPORT_DATE,
            "✅ Import cleared: SEB 2026-04-15",
            Instant.parse("2026-01-16T06:10:00Z"),
            0);

    assertThat(alert.lateMessage())
        .isEqualTo("✅ Import cleared: SEB 2026-04-15 (found 2026-01-16 08:10, posted late)");
  }
}
