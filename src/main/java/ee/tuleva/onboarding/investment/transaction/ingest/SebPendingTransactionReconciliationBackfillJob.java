package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.LOCK_HELD_MESSAGE;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.completedMessage;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.noReportsMessage;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.resumeTrigger;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.stoppedMessage;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.stoppedOutsideAReportMessage;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.windowClosedMessage;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;

import ee.tuleva.onboarding.investment.event.RunSebPendingTransactionReconciliationBackfillRequested;
import ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.ReplaySummary;
import ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.Stop;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"production", "staging"})
@RequiredArgsConstructor
class SebPendingTransactionReconciliationBackfillJob {

  private static final int BACKFILL_DAYS = 365;
  private final Clock clock;
  private final OperationsNotificationService notificationService;
  private final SebReconciliationLock reconciliationLock;
  private final SebReplayWindow replayWindow;
  private final SebPendingReportReplayer replayer;

  @EventListener
  void onSebPendingTransactionReconciliationBackfillRequested(
      RunSebPendingTransactionReconciliationBackfillRequested request) {
    if (!replayWindow.isOpenAt(clock.instant())) {
      log.warn(
          "SEB pending transactions replay refused inside the import window: closedHours={}",
          replayWindow.closedHours());
      notify(windowClosedMessage(replayWindow.closedHours(), replayWindow.opensAt()), WARNING);
      throw new IllegalStateException(
          "SEB pending transactions replay refused inside the import window: closedHours="
              + replayWindow.closedHours());
    }
    LocalDate firstReportDate =
        Optional.ofNullable(request.resumeFrom())
            .orElseGet(() -> LocalDate.now(clock).minusDays(BACKFILL_DAYS));
    if (!reconciliationLock.runIfFree(() -> run(firstReportDate))) {
      log.warn(
          "SEB pending transactions replay skipped, the reconciliation lock is held: lock={}",
          SebReconciliationLock.NAME);
      notify(LOCK_HELD_MESSAGE, WARNING);
      throw new SebReconciliationAlreadyRunningException(SebReconciliationLock.NAME);
    }
  }

  private void run(LocalDate firstReportDate) {
    ReplaySummary summary;
    try {
      summary = replayer.replayOldestFirst(firstReportDate);
    } catch (RuntimeException e) {
      log.error(
          "SEB pending transactions replay stopped outside a report: from={}", firstReportDate, e);
      notify(stoppedOutsideAReportMessage(), ERROR);
      throw new IllegalStateException(
          "SEB pending transactions replay stopped outside a report, see the log", e);
    }
    announce(summary, firstReportDate);
    summary.stop().ifPresent(SebPendingTransactionReconciliationBackfillJob::failTheTrigger);
  }

  private static void failTheTrigger(Stop stop) {
    throw new IllegalStateException(
        "SEB pending transactions replay stopped: reportDate="
            + stop.reportDate()
            + ", reason="
            + stop.reason()
            + ", resumeWith="
            + resumeTrigger(stop.reportDate()));
  }

  private void announce(ReplaySummary summary, LocalDate firstReportDate) {
    log.info(
        "SEB pending transactions replay completed: reports={}, newExecutions={},"
            + " newSettlements={}, heldBackAlerts={}, stoppedAt={}",
        summary.replayed().size(),
        summary.newExecutions(),
        summary.newSettlements(),
        summary.alerts().size(),
        summary.stop().map(Stop::reportDate).orElse(null));
    if (summary.replayed().isEmpty() && summary.stop().isEmpty()) {
      log.warn("SEB pending transactions replay found no reports: from={}", firstReportDate);
      notify(noReportsMessage(firstReportDate), WARNING);
      return;
    }
    summary
        .stop()
        .ifPresentOrElse(
            stop -> notify(stoppedMessage(stop, summary), stop.reason().severity),
            () -> sendCompleted(summary));
  }

  private void sendCompleted(ReplaySummary summary) {
    notify(
        completedMessage(summary),
        summary.alerts().isEmpty() && summary.skippedChecks().isEmpty() ? INFO : WARNING);
  }

  private void notify(String message, Severity severity) {
    try {
      if (severity == INFO) {
        notificationService.sendMessage(message, INVESTMENT);
      } else {
        notificationService.sendMessage(message, INVESTMENT, severity);
      }
    } catch (RuntimeException e) {
      log.error(
          "SEB pending transactions replay could not post its Slack notice: severity={}",
          severity,
          e);
    }
  }
}
