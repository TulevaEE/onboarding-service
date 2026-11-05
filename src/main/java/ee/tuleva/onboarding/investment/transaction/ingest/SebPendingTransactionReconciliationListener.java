package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;

import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.investment.event.trigger.JobTriggerQueue;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(SebPendingTransactionReconciliationListener.LISTENER_ORDER)
@RequiredArgsConstructor
class SebPendingTransactionReconciliationListener {

  static final int LISTENER_ORDER = 0;

  private final InvestmentReportService reportService;
  private final SebPendingTransactionReconciliationService reconciliationService;
  private final SebReconciliationLock reconciliationLock;
  private final JobTriggerQueue jobTriggerQueue;

  @EventListener
  public void onReportImportCompleted(ReportImportCompleted event) {
    if (event.provider() != SEB || event.reportType() != PENDING_TRANSACTIONS) {
      return;
    }
    Optional<InvestmentReport> report =
        reportService.getReport(event.provider(), event.reportType(), event.reportDate());
    if (report.isEmpty()) {
      log.warn(
          "SEB pending transactions report not found for event: reportDate={}", event.reportDate());
      return;
    }
    if (!reconciliationLock.runIfFree(
        stillHeld -> reconciliationService.reconcile(report.get(), stillHeld))) {
      log.info(
          "SEB pending transactions report reconciliation deferred, its lock is held:"
              + " reportDate={}, trigger={}",
          event.reportDate(),
          SebPendingTransactionReconciliationJob.TRIGGER);
      jobTriggerQueue.enqueueUnlessPending(SebPendingTransactionReconciliationJob.TRIGGER);
    }
  }
}
