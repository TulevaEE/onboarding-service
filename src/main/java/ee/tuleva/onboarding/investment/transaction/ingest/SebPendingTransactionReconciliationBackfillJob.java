package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;

import ee.tuleva.onboarding.investment.event.RunSebPendingTransactionReconciliationBackfillRequested;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
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

  private static final int BACKFILL_DAYS = 30;

  private final Clock clock;
  private final InvestmentReportService reportService;
  private final SebPendingTransactionReconciliationService reconciliationService;

  @EventListener
  void onSebPendingTransactionReconciliationBackfillRequested(
      RunSebPendingTransactionReconciliationBackfillRequested event) {
    run();
  }

  void run() {
    LocalDate today = LocalDate.now(clock);
    log.info(
        "Running SEB pending transactions reconciliation backfill: today={}, backfillDays={}",
        today,
        BACKFILL_DAYS);

    int daysScanned = 0;
    int reportsFound = 0;
    for (int i = 0; i <= BACKFILL_DAYS; i++) {
      LocalDate date = today.minusDays(i);
      daysScanned++;
      try {
        Optional<InvestmentReport> report =
            reportService.getReport(SEB, PENDING_TRANSACTIONS, date);
        if (report.isEmpty()) {
          continue;
        }
        reportsFound++;
        reconciliationService.reconcile(report.get());
      } catch (Exception e) {
        log.error(
            "SEB pending transactions reconciliation backfill failed, continuing: reportDate={}",
            date,
            e);
      }
    }

    log.info(
        "SEB pending transactions reconciliation backfill completed: daysScanned={},"
            + " reportsFound={}",
        daysScanned,
        reportsFound);
  }
}
