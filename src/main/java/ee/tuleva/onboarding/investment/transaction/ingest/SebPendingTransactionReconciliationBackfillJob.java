package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.LOCK_HELD_MESSAGE;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.completedMessage;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.noReportsMessage;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.skippedCheckReason;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.stoppedMessage;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.stoppedOutsideAReportMessage;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.windowClosedMessage;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;

import ee.tuleva.onboarding.investment.event.RunSebPendingTransactionReconciliationBackfillRequested;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementRepository;
import ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.ReplaySummary;
import ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.SkippedCheck;
import ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.Stop;
import ee.tuleva.onboarding.investment.transaction.ingest.SebPendingReplayMessages.StopReason;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

  static final int BACKFILL_DAYS = 365;
  private final Clock clock;
  private final InvestmentReportService reportService;
  private final SebPendingTransactionReconciliationService reconciliationService;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionSettlementRepository settlementRepository;
  private final OperationsNotificationService notificationService;
  private final SebReconciliationLock reconciliationLock;
  private final SebReplayWindow replayWindow;

  @EventListener(RunSebPendingTransactionReconciliationBackfillRequested.class)
  void onSebPendingTransactionReconciliationBackfillRequested() {
    if (!replayWindow.isOpenAt(clock.instant())) {
      log.warn(
          "SEB pending transactions replay refused inside the import window: closedHours={}",
          replayWindow.closedHours());
      notify(windowClosedMessage(replayWindow.closedHours(), replayWindow.opensAt()), WARNING);
      throw new IllegalStateException(
          "SEB pending transactions replay refused inside the import window: closedHours="
              + replayWindow.closedHours());
    }
    if (!reconciliationLock.runIfFree(this::run)) {
      log.warn(
          "SEB pending transactions replay skipped, the reconciliation lock is held: lock={}",
          SebReconciliationLock.NAME);
      notify(LOCK_HELD_MESSAGE, WARNING);
      throw new SebReconciliationAlreadyRunningException(SebReconciliationLock.NAME);
    }
  }

  private void run() {
    ReplaySummary summary;
    try {
      summary = replayOldestFirst();
    } catch (RuntimeException e) {
      log.error(
          "SEB pending transactions replay stopped outside a report: backfillDays={}",
          BACKFILL_DAYS,
          e);
      notify(stoppedOutsideAReportMessage(), ERROR);
      throw new IllegalStateException(
          "SEB pending transactions replay stopped outside a report, see the log", e);
    }
    announce(summary);
    summary.stop().ifPresent(SebPendingTransactionReconciliationBackfillJob::failTheTrigger);
  }

  private static void failTheTrigger(Stop stop) {
    throw new IllegalStateException(
        "SEB pending transactions replay stopped: reportDate="
            + stop.reportDate()
            + ", reason="
            + stop.reason());
  }

  private ReplaySummary replayOldestFirst() {
    long executionsBefore = executionRepository.count();
    long settlementsBefore = settlementRepository.count();
    List<ReconciliationAlert> heldBackAlerts = new ArrayList<>();
    List<SkippedCheck> skippedChecks = new ArrayList<>();
    List<LocalDate> replayed = new ArrayList<>();
    Optional<Stop> stop = Optional.empty();
    LocalDate today = LocalDate.now(clock);
    for (int daysAgo = BACKFILL_DAYS; daysAgo >= 0; daysAgo--) {
      LocalDate date = today.minusDays(daysAgo);
      List<ReconciliationAlert> reportAlerts = new ArrayList<>();
      SettlementCheck check;
      try {
        Optional<InvestmentReport> report =
            reportService.getReport(SEB, PENDING_TRANSACTIONS, date);
        if (report.isEmpty()) {
          continue;
        }
        if (!replayWindow.isOpenAt(clock.instant())) {
          log.warn(
              "SEB pending transactions replay stopped as the import window opens: reportDate={},"
                  + " closedHours={}",
              date,
              replayWindow.closedHours());
          stop = Optional.of(new Stop(date, StopReason.IMPORT_WINDOW_OPENS));
          break;
        }
        check = reconciliationService.replay(report.get(), reportAlerts::add);
      } catch (RuntimeException e) {
        log.error("SEB pending transactions replay stopped: reportDate={}", date, e);
        stop = Optional.of(new Stop(date, StopReason.REPORT_FAILED));
        break;
      }
      reportAlerts.forEach(
          alert -> log.info("SEB pending transactions replay held back alert: alert={}", alert));
      heldBackAlerts.addAll(reportAlerts);
      skippedCheckReason(check)
          .ifPresent(reason -> skippedChecks.add(new SkippedCheck(date, reason)));
      replayed.add(date);
    }
    return new ReplaySummary(
        List.copyOf(replayed),
        executionRepository.count() - executionsBefore,
        settlementRepository.count() - settlementsBefore,
        List.copyOf(heldBackAlerts),
        List.copyOf(skippedChecks),
        stop);
  }

  private void announce(ReplaySummary summary) {
    log.info(
        "SEB pending transactions replay completed: reports={}, newExecutions={},"
            + " newSettlements={}, heldBackAlerts={}, stoppedAt={}",
        summary.replayed().size(),
        summary.newExecutions(),
        summary.newSettlements(),
        summary.alerts().size(),
        summary.stop().map(Stop::reportDate).orElse(null));
    if (summary.replayed().isEmpty() && summary.stop().isEmpty()) {
      log.warn("SEB pending transactions replay found no reports: backfillDays={}", BACKFILL_DAYS);
      notify(noReportsMessage(), WARNING);
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
