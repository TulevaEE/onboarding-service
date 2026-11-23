package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class HealthCheckAlertTest {

  private static final LocalDate REPORT_DATE = LocalDate.of(2026, 4, 15);
  private static final Instant FOUND_AT = Instant.parse("2026-04-16T06:10:00Z");
  private static final String BLOCKED =
      "IMPORT BLOCKED: SEB 2026-04-15 — TUK75 not imported, source files need to be fixed\n"
          + "Funds: TUK75 blocked\n"
          + "\n🛑 [FAIL] ISIN_MATCH TUK75: TUK75: unknown ISIN";

  @Test
  void anAlertPostedWithinFiveMinutesOfItsStateBeingFoundReadsAsItWasBuilt() {
    var alert = alert(BLOCKED, FOUND_AT);

    assertThat(alert.messagePostedAt(FOUND_AT.plus(Duration.ofMinutes(5)))).isEqualTo(BLOCKED);
  }

  @Test
  void anAlertPostedLaterSaysWhenItsStateWasFoundInTallinnTimeOnItsFirstLine() {
    var alert = alert(BLOCKED, FOUND_AT);

    assertThat(alert.messagePostedAt(FOUND_AT.plus(Duration.ofMinutes(5)).plusSeconds(1)))
        .isEqualTo(
            "IMPORT BLOCKED: SEB 2026-04-15 — TUK75 not imported, source files need to be fixed"
                + " (found 2026-04-16 09:10, posted late)\n"
                + "Funds: TUK75 blocked\n"
                + "\n🛑 [FAIL] ISIN_MATCH TUK75: TUK75: unknown ISIN");
  }

  @Test
  void aOneLineAlertPostedLateSaysWhenItsStateWasFoundAtItsEnd() {
    var foundInWinter = Instant.parse("2026-01-16T06:10:00Z");
    var alert = alert("✅ Import cleared: SEB 2026-04-15", foundInWinter);

    assertThat(alert.messagePostedAt(foundInWinter.plus(Duration.ofHours(1))))
        .isEqualTo("✅ Import cleared: SEB 2026-04-15 (found 2026-01-16 08:10, posted late)");
  }

  private static HealthCheckAlert alert(String message, Instant foundAt) {
    return new HealthCheckAlert(1L, SEB, REPORT_DATE, message, foundAt, 0, null);
  }
}
