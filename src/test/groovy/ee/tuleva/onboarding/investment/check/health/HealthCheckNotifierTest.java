package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.*;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.COMPLETENESS;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.ISIN_MATCH;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.NAV_FLOW_CONSISTENCY;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.UNSETTLED_TRADES;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthCheckNotifierTest {

  private static final LocalDate DATE = LocalDate.of(2026, 4, 15);

  @Mock OperationsNotificationService notificationService;
  @Mock HealthCheckEventRepository eventRepository;
  @InjectMocks HealthCheckNotifier notifier;

  @Test
  void silentWhenNoIssuesAndNoPriorState() {
    var result = new HealthCheckResult(TUK75, DATE, List.of());

    var notified = notifier.notify(SEB, DATE, List.of(result), Map.of());

    assertThat(notified).isFalse();
    verifyNoInteractions(notificationService);
  }

  @Test
  void sendsImportBlockedOnFirstFail() {
    var finding = new HealthCheckFinding(TUK75, ISIN_MATCH, FAIL, "TUK75: unknown ISIN");
    var result = new HealthCheckResult(TUK75, DATE, List.of(finding));

    var notified = notifier.notify(SEB, DATE, List.of(result), Map.of());

    assertThat(notified).isTrue();
    verify(notificationService).sendMessage(contains("IMPORT BLOCKED"), eq(INVESTMENT));
    verify(notificationService)
        .sendMessage(contains("source files need to be fixed"), eq(INVESTMENT));
    verify(notificationService).sendMessage(contains("[FAIL]"), eq(INVESTMENT));
  }

  @Test
  void aBlockedImportSaysHowToGetACorrectedFileIn() {
    var finding = new HealthCheckFinding(TUK75, ISIN_MATCH, FAIL, "TUK75: unknown ISIN");

    notifier.notify(
        SEB, DATE, List.of(new HealthCheckResult(TUK75, DATE, List.of(finding))), Map.of());

    verify(notificationService)
        .sendMessage(
            contains(
                "Fix: ask SEB to resend the file, or email a corrected"
                    + " TULEVA_pos_raport_20260415_korrigeeritud.csv from funds@tuleva.ee to"
                    + " funds@tuleva.ee only"),
            eq(INVESTMENT));
  }

  @Test
  void theHeaderGivesEveryFundsImportStatus() {
    var failed = new HealthCheckFinding(TUK75, ISIN_MATCH, FAIL, "TUK75: unknown ISIN");
    var warned = new HealthCheckFinding(TUV100, COMPLETENESS, WARNING, "TUV100: no CASH");

    notifier.notify(
        SEB,
        DATE,
        List.of(
            new HealthCheckResult(TUK75, DATE, List.of(failed)),
            new HealthCheckResult(TUV100, DATE, List.of(warned)),
            new HealthCheckResult(TUK00, DATE, List.of())),
        Map.of());

    verify(notificationService)
        .sendMessage(
            contains("Funds: TUK75 blocked, TUV100 imported with warnings, TUK00 imported"),
            eq(INVESTMENT));
  }

  @Test
  void importBlockedHeaderNamesOnlyTheBlockedFunds() {
    var failed = new HealthCheckFinding(TUK75, ISIN_MATCH, FAIL, "TUK75: unknown ISIN");
    var warned = new HealthCheckFinding(TUV100, COMPLETENESS, WARNING, "TUV100: no CASH");

    notifier.notify(
        SEB,
        DATE,
        List.of(
            new HealthCheckResult(TUK75, DATE, List.of(failed)),
            new HealthCheckResult(TUV100, DATE, List.of(warned))),
        Map.of());

    verify(notificationService)
        .sendMessage(
            contains("IMPORT BLOCKED: SEB 2026-04-15 — TUK75 not imported"), eq(INVESTMENT));
  }

  @Test
  void aBlockedFundWhosePositionsWereImportedEarlierSaysTheyStayInUse() {
    var stalePayable =
        new HealthCheckFinding(
            TUK75,
            UNSETTLED_TRADES,
            FAIL,
            "Trade payables 2000.00, open buys 1000.00, excess 1000.00, NAV too low."
                + " No trade left SEB's trade list");
    var unknownIsin = new HealthCheckFinding(TUV100, ISIN_MATCH, FAIL, "TUV100: unknown ISIN");

    notifier.notify(
        SEB,
        DATE,
        List.of(
            new HealthCheckResult(TUK75, DATE, List.of(stalePayable)),
            new HealthCheckResult(TUV100, DATE, List.of(unknownIsin))),
        Map.of(TUK75, Instant.parse("2026-04-16T06:10:00Z")));

    verify(notificationService)
        .sendMessage(
            startsWith(
                "IMPORT BLOCKED: SEB 2026-04-15 — TUV100 not imported; TUK75 positions from"
                    + " 2026-04-16 09:10 stay in use for NAV, source files need to be fixed\n"
                    + "Funds: TUK75 blocked (earlier positions in use), TUV100 blocked\n"),
            eq(INVESTMENT));
  }

  @Test
  void sendsWarningWhenNoFails() {
    var finding = new HealthCheckFinding(TUK75, COMPLETENESS, WARNING, "TUK75: no CASH");
    var result = new HealthCheckResult(TUK75, DATE, List.of(finding));

    var notified = notifier.notify(SEB, DATE, List.of(result), Map.of());

    assertThat(notified).isTrue();
    verify(notificationService).sendMessage(contains("Import warning"), eq(INVESTMENT));
    verify(notificationService).sendMessage(contains("[WARNING]"), eq(INVESTMENT));
  }

  // A check that declined to answer is not a clean import and not a warning about the fund, so
  // it has to read as its own thing rather than borrowing either headline.
  @Test
  void sendsCouldNotRunWhenACheckDeclinedToAnswer() {
    var finding =
        new HealthCheckFinding(
            TUK75, NAV_FLOW_CONSISTENCY, NOT_RUN, "TUK75: unpricedHoldings=IE00A");
    var result = new HealthCheckResult(TUK75, DATE, List.of(finding));

    var notified = notifier.notify(SEB, DATE, List.of(result), Map.of());

    assertThat(notified).isTrue();
    verify(notificationService).sendMessage(contains("could not run"), eq(INVESTMENT));
    verify(notificationService).sendMessage(contains("[NOT_RUN]"), eq(INVESTMENT));
  }

  @Test
  void includesProviderAndDateInMessage() {
    var finding = new HealthCheckFinding(TUK75, ISIN_MATCH, FAIL, "test");
    var result = new HealthCheckResult(TUK75, DATE, List.of(finding));

    notifier.notify(SEB, DATE, List.of(result), Map.of());

    verify(notificationService).sendMessage(contains("SEB"), eq(INVESTMENT));
    verify(notificationService).sendMessage(contains("2026-04-15"), eq(INVESTMENT));
  }

  @Test
  void silentWhenSeverityUnchangedFromPriorRun() {
    var finding = new HealthCheckFinding(TUK75, ISIN_MATCH, FAIL, "TUK75: unknown ISIN");
    var result = new HealthCheckResult(TUK75, DATE, List.of(finding));
    givenPreviousSeverity(ISIN_MATCH, FAIL);

    var notified = notifier.notify(SEB, DATE, List.of(result), Map.of());

    assertThat(notified).isFalse();
    verifyNoInteractions(notificationService);
  }

  @Test
  void sendsWarningWhenSeverityDropsFromFailToWarning() {
    var finding = new HealthCheckFinding(TUK75, ISIN_MATCH, WARNING, "TUK75: soft issue");
    var result = new HealthCheckResult(TUK75, DATE, List.of(finding));
    givenPreviousSeverity(ISIN_MATCH, FAIL);

    var notified = notifier.notify(SEB, DATE, List.of(result), Map.of());

    assertThat(notified).isTrue();
    verify(notificationService).sendMessage(contains("Import warning"), eq(INVESTMENT));
  }

  @Test
  void sendsClearedWhenResolvedAfterPriorFail() {
    var result = new HealthCheckResult(TUK75, DATE, List.of());
    givenPreviousSeverity(ISIN_MATCH, FAIL);

    var notified = notifier.notify(SEB, DATE, List.of(result), Map.of());

    assertThat(notified).isTrue();
    verify(notificationService).sendMessage(contains("Import cleared"), eq(INVESTMENT));
    verify(notificationService).sendMessage(contains("[CLEARED]"), eq(INVESTMENT));
  }

  @Test
  void swallowsExceptions() {
    doThrow(new RuntimeException("Slack down")).when(notificationService).sendMessage(any(), any());
    var finding = new HealthCheckFinding(TUK75, ISIN_MATCH, FAIL, "test");
    var result = new HealthCheckResult(TUK75, DATE, List.of(finding));

    var notified = notifier.notify(SEB, DATE, List.of(result), Map.of());

    assertThat(notified).isFalse();
  }

  // A check that could not run for a new reason needs saying out loud: the reasons call for
  // different work, and severity alone cannot tell a missing threshold row from an unexplained
  // exit.
  @Test
  void sendsAgainWhenTheReasonACheckCouldNotRunChanges() {
    givenPreviousNotRun(NAV_FLOW_CONSISTENCY, "could not be reconciled: no threshold configured");
    var finding =
        new HealthCheckFinding(
            TUK75,
            NAV_FLOW_CONSISTENCY,
            NOT_RUN,
            "could not be reconciled: unexplainedExits=IE00A");
    var result = new HealthCheckResult(TUK75, DATE, List.of(finding));

    assertThat(notifier.notify(SEB, DATE, List.of(result), Map.of())).isTrue();
  }

  @Test
  void silentWhenTheSameCheckCouldNotRunForTheSameReason() {
    givenPreviousNotRun(NAV_FLOW_CONSISTENCY, "could not be reconciled: no threshold configured");
    var finding =
        new HealthCheckFinding(
            TUK75,
            NAV_FLOW_CONSISTENCY,
            NOT_RUN,
            "could not be reconciled: no threshold configured");
    var result = new HealthCheckResult(TUK75, DATE, List.of(finding));

    assertThat(notifier.notify(SEB, DATE, List.of(result), Map.of())).isFalse();
  }

  @Test
  void importBlockedHeaderNamesAFundWhoseFailIsUnchangedSinceTheLastRun() {
    givenPreviousSeverity(ISIN_MATCH, FAIL);
    var unchangedFail = new HealthCheckFinding(TUK75, ISIN_MATCH, FAIL, "TUK75: unknown ISIN");
    var newFail = new HealthCheckFinding(TUV100, COMPLETENESS, FAIL, "TUV100: negative SECURITY");

    notifier.notify(
        SEB,
        DATE,
        List.of(
            new HealthCheckResult(TUK75, DATE, List.of(unchangedFail)),
            new HealthCheckResult(TUV100, DATE, List.of(newFail))),
        Map.of());

    verify(notificationService)
        .sendMessage(
            contains("IMPORT BLOCKED: SEB 2026-04-15 — TUK75, TUV100 not imported"),
            eq(INVESTMENT));
  }

  private void givenPreviousNotRun(HealthCheckType checkType, String message) {
    var stored =
        HealthCheckEvent.builder()
            .severity(NOT_RUN)
            .result(Map.of("findings", List.of(Map.of("message", message))))
            .build();
    lenient()
        .doReturn(List.of(stored, stored))
        .when(eventRepository)
        .findTop2ByFundAndCheckDateAndCheckTypeOrderByCreatedAtDesc(TUK75, DATE, checkType);
  }

  private void givenPreviousSeverity(HealthCheckType checkType, HealthCheckSeverity severity) {
    var current = HealthCheckEvent.builder().severity(severity).build();
    var previous = HealthCheckEvent.builder().severity(severity).build();
    lenient()
        .doReturn(List.of(current, previous))
        .when(eventRepository)
        .findTop2ByFundAndCheckDateAndCheckTypeOrderByCreatedAtDesc(TUK75, DATE, checkType);
  }
}
