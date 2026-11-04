package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.event.RunSebPendingTransactionReconciliationBackfillRequested;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  private static final Map<SettlementCheck, String> SKIPPED_CHECK_REASONS =
      Map.of(
          SettlementCheck.SKIPPED_EMPTY_REPORT, "tühi raport",
          SettlementCheck.SKIPPED_NO_MATCHED_ROW, "ükski rida ei sobitunud",
          SettlementCheck.SKIPPED_MALFORMED_ROWS, "vigased read",
          SettlementCheck.SKIPPED_NOT_LATEST_REPORT, "polnud viimane raport");
  private static final String LOCK_HELD_MESSAGE =
      "SEB ootel-tehingute raportite uuesti sobitamine ei alanud, sest SEB ootel-tehingute"
          + " sobitamine juba käib.\nKäivita uuesti, kui see on lõppenud.";
  private static final String RERUN_ADDS_NOTHING_TWICE =
      "Uuesti käivitamine ei lisa täitmisi ega arveldusi topelt.";

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
      notificationService.sendMessage(windowClosedMessage(), INVESTMENT, WARNING);
      throw new IllegalStateException(
          "SEB pending transactions replay refused inside the import window: closedHours="
              + replayWindow.closedHours());
    }
    if (!reconciliationLock.runIfFree(this::run)) {
      log.warn(
          "SEB pending transactions replay skipped, the reconciliation lock is held: lock={}",
          SebReconciliationLock.NAME);
      notificationService.sendMessage(LOCK_HELD_MESSAGE, INVESTMENT, WARNING);
      throw new SebReconciliationAlreadyRunningException(SebReconciliationLock.NAME);
    }
  }

  private void run() {
    try {
      announce(replayOldestFirst());
    } catch (RuntimeException e) {
      log.error(
          "SEB pending transactions replay stopped outside a report: backfillDays={}",
          BACKFILL_DAYS,
          e);
      notificationService.sendMessage(stoppedOutsideAReportMessage(), INVESTMENT, ERROR);
    }
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
      Optional.ofNullable(SKIPPED_CHECK_REASONS.get(check))
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
      notificationService.sendMessage(noReportsMessage(), INVESTMENT, WARNING);
      return;
    }
    summary
        .stop()
        .ifPresentOrElse(
            stop ->
                notificationService.sendMessage(
                    stoppedMessage(stop, summary), INVESTMENT, stop.reason().severity),
            () -> sendCompleted(summary));
  }

  private void sendCompleted(ReplaySummary summary) {
    if (summary.alerts().isEmpty() && summary.skippedChecks().isEmpty()) {
      notificationService.sendMessage(completedMessage(summary), INVESTMENT);
    } else {
      notificationService.sendMessage(completedMessage(summary), INVESTMENT, WARNING);
    }
  }

  private String windowClosedMessage() {
    return "SEB ootel-tehingute raportite uuesti sobitamine ei alanud, sest kell "
        + replayWindow.closedHours()
        + " imporditakse SEB raporteid ja käib igapäevane sobitamine.\nKäivita uuesti pärast kella "
        + replayWindow.opensAt()
        + ".";
  }

  private static String noReportsMessage() {
    return "SEB ootel-tehingute raporteid pole viimase "
        + BACKFILL_DAYS
        + " päeva kohta, midagi ei sobitatud uuesti.";
  }

  private static String stoppedOutsideAReportMessage() {
    return "SEB ootel-tehingute raportite uuesti sobitamine peatus ootamatult (põhjus on logis).\n"
        + "Paranda põhjus ja käivita uuesti. "
        + RERUN_ADDS_NOTHING_TWICE;
  }

  private static String completedMessage(ReplaySummary summary) {
    return "SEB ootel-tehingute raportid sobitati uuesti vanimast uusimani. Raporteid: "
        + reportsAndRange(summary.replayed())
        + ".\n"
        + outcome(summary)
        + "Lahknevused ja kärbitud raportid on auditis (investment_transaction_audit_event). "
        + RERUN_ADDS_NOTHING_TWICE;
  }

  private static String stoppedMessage(Stop stop, ReplaySummary summary) {
    return "SEB ootel-tehingute raportite uuesti sobitamine peatus "
        + stop.reason().where.formatted(stop.reportDate())
        + (summary.replayed().isEmpty()
            ? "Enne seda ei sobitatud ühtki raportit.\n"
            : "Enne seda raporteid: "
                + reportsAndRange(summary.replayed())
                + ". "
                + outcome(summary))
        + stop.reason().advice
        + RERUN_ADDS_NOTHING_TWICE;
  }

  private static String reportsAndRange(List<LocalDate> replayed) {
    return replayed.size() == 1
        ? "1 (" + replayed.getFirst() + ")"
        : replayed.size() + " (" + replayed.getFirst() + " kuni " + replayed.getLast() + ")";
  }

  private static String outcome(ReplaySummary summary) {
    List<ReconciliationAlert> alerts = summary.alerts();
    return "Uusi täitmisi: "
        + summary.newExecutions()
        + ", uusi arveldusi: "
        + summary.newSettlements()
        + ". Saatmata hoiatusi: "
        + alerts.size()
        + " (koguse või summa lahknevusi: "
        + count(alerts, QuantityAmountMismatchEvent.class)
        + ", kärbitud raporteid: "
        + count(alerts, PossibleReportTruncationEvent.class)
        + ", tükkide hinnaerinevusi: "
        + count(alerts, ExecutionPriceConsistencyEvent.class)
        + ").\n"
        + skippedChecksLine(summary.skippedChecks())
        + priceDivergenceLines(alerts);
  }

  private static long count(
      List<ReconciliationAlert> alerts, Class<? extends ReconciliationAlert> kind) {
    return alerts.stream().filter(kind::isInstance).count();
  }

  private static String skippedChecksLine(List<SkippedCheck> skippedChecks) {
    return skippedChecks.isEmpty()
        ? ""
        : skippedChecks.stream()
            .map(skipped -> skipped.reportDate() + " (" + skipped.reason() + ")")
            .collect(joining(", ", "Arvelduse kontroll jäi vahele: ", ".\n"));
  }

  private static String priceDivergenceLines(List<ReconciliationAlert> alerts) {
    return alerts.stream()
        .filter(ExecutionPriceConsistencyEvent.class::isInstance)
        .map(ExecutionPriceConsistencyEvent.class::cast)
        .map(
            divergence ->
                "Tükkide hinnaerinevus: tellimus "
                    + divergence.orderId()
                    + ", "
                    + divergence.isin()
                    + ", raport "
                    + divergence.reportDate()
                    + ".\n")
        .collect(joining());
  }

  private record ReplaySummary(
      List<LocalDate> replayed,
      long newExecutions,
      long newSettlements,
      List<ReconciliationAlert> alerts,
      List<SkippedCheck> skippedChecks,
      Optional<Stop> stop) {}

  private record SkippedCheck(LocalDate reportDate, String reason) {}

  private record Stop(LocalDate reportDate, StopReason reason) {}

  @RequiredArgsConstructor
  private enum StopReason {
    REPORT_FAILED("raportil %s (põhjus on logis).\n", "Paranda põhjus ja käivita uuesti. ", ERROR),
    IMPORT_WINDOW_OPENS(
        "enne raportit %s, sest algas SEB raportite impordi aeg.\n",
        "Käivita uuesti õhtul, kui SEB raporteid ei impordita. ", WARNING);

    private final String where;
    private final String advice;
    private final Severity severity;
  }
}
