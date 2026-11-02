package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;

import ee.tuleva.onboarding.investment.event.RunSebPendingTransactionReconciliationBackfillRequested;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
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
  private static final String RERUN_IS_SAFE =
      "Uuesti käivitamine on ohutu: juba sobitatut topelt ei lisata.";

  private final Clock clock;
  private final InvestmentReportService reportService;
  private final SebPendingTransactionReconciliationService reconciliationService;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionSettlementRepository settlementRepository;
  private final OperationsNotificationService notificationService;

  @EventListener(RunSebPendingTransactionReconciliationBackfillRequested.class)
  void onSebPendingTransactionReconciliationBackfillRequested() {
    run();
  }

  void run() {
    List<InvestmentReport> reports = storedReportsOldestFirst(LocalDate.now(clock));
    if (reports.isEmpty()) {
      log.warn("SEB pending transactions replay found no reports: backfillDays={}", BACKFILL_DAYS);
      notificationService.sendMessage(noReportsMessage(), INVESTMENT, WARNING);
      return;
    }
    long executionsBefore = executionRepository.count();
    long settlementsBefore = settlementRepository.count();
    List<Object> heldBackAlerts = new ArrayList<>();
    List<LocalDate> replayed = new ArrayList<>();
    Optional<LocalDate> stoppedAt = Optional.empty();
    for (InvestmentReport report : reports) {
      try {
        reconciliationService.replay(report, alert -> holdBack(alert, heldBackAlerts));
        replayed.add(report.getReportDate());
      } catch (RuntimeException e) {
        log.error(
            "SEB pending transactions replay stopped: reportDate={}", report.getReportDate(), e);
        stoppedAt = Optional.of(report.getReportDate());
        break;
      }
    }
    ReplaySummary summary =
        new ReplaySummary(
            replayed,
            executionRepository.count() - executionsBefore,
            settlementRepository.count() - settlementsBefore,
            heldBackAlerts.size());
    log.info(
        "SEB pending transactions replay completed: reports={}, newExecutions={},"
            + " newSettlements={}, heldBackAlerts={}, stoppedAt={}",
        replayed.size(),
        summary.newExecutions(),
        summary.newSettlements(),
        summary.heldBackAlerts(),
        stoppedAt.orElse(null));
    stoppedAt.ifPresentOrElse(
        date -> notificationService.sendMessage(stoppedMessage(date, summary), INVESTMENT, ERROR),
        () -> notificationService.sendMessage(completedMessage(summary), INVESTMENT));
  }

  private List<InvestmentReport> storedReportsOldestFirst(LocalDate today) {
    return IntStream.iterate(BACKFILL_DAYS, daysAgo -> daysAgo >= 0, daysAgo -> daysAgo - 1)
        .mapToObj(today::minusDays)
        .map(date -> reportService.getReport(SEB, PENDING_TRANSACTIONS, date))
        .flatMap(Optional::stream)
        .toList();
  }

  private static void holdBack(Object alert, List<Object> heldBackAlerts) {
    log.info("SEB pending transactions replay held back alert: alert={}", alert);
    heldBackAlerts.add(alert);
  }

  private static String noReportsMessage() {
    return "SEB ootel-raportite ajaloo uuesti sobitamine: viimase "
        + BACKFILL_DAYS
        + " päeva kohta pole ühtki raportit, midagi ei sobitatud.";
  }

  private static String completedMessage(ReplaySummary summary) {
    return "SEB ootel-raportite ajalugu sobitati uuesti kuupäevade järjekorras: "
        + summary.reportsAndRange()
        + ".\nUusi täitmisi "
        + summary.newExecutions()
        + ", uusi arveldusi "
        + summary.newSettlements()
        + ". Hoiatusi jäeti saatmata "
        + summary.heldBackAlerts()
        + ", need on logis.\n"
        + RERUN_IS_SAFE;
  }

  private static String stoppedMessage(LocalDate stoppedAt, ReplaySummary summary) {
    return "SEB ootel-raportite ajaloo uuesti sobitamine peatus raportil "
        + stoppedAt
        + " (põhjus logis).\n"
        + (summary.replayed().isEmpty()
            ? "Enne seda ei sobitatud ühtki raportit."
            : "Enne seda "
                + summary.reportsAndRange()
                + ": uusi täitmisi "
                + summary.newExecutions()
                + ", uusi arveldusi "
                + summary.newSettlements()
                + ", hoiatusi jäeti saatmata "
                + summary.heldBackAlerts()
                + ".")
        + "\nParanda põhjus ja käivita uuesti: juba sobitatut topelt ei lisata.";
  }

  private record ReplaySummary(
      List<LocalDate> replayed, long newExecutions, long newSettlements, int heldBackAlerts) {

    String reportsAndRange() {
      return (replayed.size() == 1 ? "1 raport" : replayed.size() + " raportit")
          + ", "
          + replayed.getFirst()
          + " kuni "
          + replayed.getLast();
    }
  }
}
