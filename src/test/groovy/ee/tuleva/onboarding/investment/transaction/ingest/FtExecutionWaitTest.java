package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.MissingExecution.Stage.AWAITING_SEB_REPORT;
import static ee.tuleva.onboarding.investment.transaction.ingest.MissingExecution.Stage.MISSING_AFTER_SEB_REPORT;
import static ee.tuleva.onboarding.investment.transaction.ingest.MissingExecution.Stage.SEB_REPORT_NOT_RECONCILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FtExecutionWaitTest {

  private static final LocalDate TUESDAY_TRADE = LocalDate.of(2026, 11, 17);
  private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 18);
  private static final LocalDate THURSDAY = LocalDate.of(2026, 11, 19);
  private static final LocalDate FRIDAY_TRADE = LocalDate.of(2026, 11, 20);
  private static final LocalDate MONDAY = LocalDate.of(2026, 11, 23);

  @Mock private ReconciliationAuditRecorder reconciliationRecorder;

  private FtExecutionWait waitAt(String instant) {
    return new FtExecutionWait(
        reconciliationRecorder,
        new EstonianCalendar(new PublicHolidays()),
        Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
  }

  @Test
  void beforeSebsReportForTheDayAfterTheTradeIsReconciled_theExecutionIsStillAwaited() {
    given(reconciliationRecorder.firstReportReconciledAsOfOrAfter(WEDNESDAY))
        .willReturn(Optional.empty());

    MissingExecution missing = waitAt("2026-11-19T06:00:00Z").assess(TUESDAY_TRADE);

    assertThat(missing)
        .isEqualTo(
            new MissingExecution(
                AWAITING_SEB_REPORT,
                Map.of(
                    "awaitingSebReportAsOf", "2026-11-18",
                    "executionPendingUntil", "2026-11-19")));
  }

  @Test
  void onceSebsReportForTheDayAfterTheTradeIsReconciled_theExecutionIsMissing() {
    given(reconciliationRecorder.firstReportReconciledAsOfOrAfter(WEDNESDAY))
        .willReturn(Optional.of(WEDNESDAY));

    MissingExecution missing = waitAt("2026-11-19T09:00:00Z").assess(TUESDAY_TRADE);

    assertThat(missing)
        .isEqualTo(
            new MissingExecution(
                MISSING_AFTER_SEB_REPORT,
                Map.of(
                    "executionMissing", "no execution after SEB pending report as of 2026-11-18",
                    "sebReportAsOf", "2026-11-18")));
  }

  @Test
  void aLaterSebReportReconciledInstead_namesThatReport() {
    given(reconciliationRecorder.firstReportReconciledAsOfOrAfter(WEDNESDAY))
        .willReturn(Optional.of(THURSDAY));

    MissingExecution missing = waitAt("2026-11-20T09:00:00Z").assess(TUESDAY_TRADE);

    assertThat(missing.details()).containsEntry("sebReportAsOf", "2026-11-19");
    assertThat(missing.awaitsSebReport()).isFalse();
  }

  @Test
  void noSebReportReconciledByTwoBusinessDaysAfterTheTrade_theReportItselfIsOverdue() {
    given(reconciliationRecorder.firstReportReconciledAsOfOrAfter(WEDNESDAY))
        .willReturn(Optional.empty());

    MissingExecution missing = waitAt("2026-11-20T06:00:00Z").assess(TUESDAY_TRADE);

    assertThat(missing)
        .isEqualTo(
            new MissingExecution(
                SEB_REPORT_NOT_RECONCILED,
                Map.of(
                    "executionMissing",
                    "no SEB pending report as of 2026-11-18 or later reconciled by 2026-11-19",
                    "sebReportMissingAsOf",
                    "2026-11-18")));
  }

  @Test
  void onTheLastDayOfTheWait_withoutAReconciledReport_theExecutionIsStillAwaited() {
    given(reconciliationRecorder.firstReportReconciledAsOfOrAfter(WEDNESDAY))
        .willReturn(Optional.empty());

    MissingExecution missing = waitAt("2026-11-19T21:59:00Z").assess(TUESDAY_TRADE);

    assertThat(missing.awaitsSebReport()).isTrue();
  }

  @Test
  void aTradeBeforeMidsummer_waitsForTheFirstEstonianWorkingDaysReport() {
    LocalDate mondayBeforeMidsummer = LocalDate.of(2026, 6, 22);
    given(reconciliationRecorder.firstReportReconciledAsOfOrAfter(LocalDate.of(2026, 6, 25)))
        .willReturn(Optional.empty());

    MissingExecution missing = waitAt("2026-06-26T06:00:00Z").assess(mondayBeforeMidsummer);

    assertThat(missing.details())
        .isEqualTo(
            Map.of(
                "awaitingSebReportAsOf", "2026-06-25",
                "executionPendingUntil", "2026-06-26"));
  }

  @Test
  void aFridayTrade_waitsForMondaysReportAcrossTheWeekend() {
    given(reconciliationRecorder.firstReportReconciledAsOfOrAfter(MONDAY))
        .willReturn(Optional.empty());

    MissingExecution missing = waitAt("2026-11-24T06:00:00Z").assess(FRIDAY_TRADE);

    assertThat(missing.details())
        .isEqualTo(
            Map.of(
                "awaitingSebReportAsOf", "2026-11-23",
                "executionPendingUntil", "2026-11-24"));
  }
}
