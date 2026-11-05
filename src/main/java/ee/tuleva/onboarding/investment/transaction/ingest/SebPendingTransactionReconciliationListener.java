package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;

import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.investment.event.RunSebPendingTransactionReportReconciliationRequested;
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
    if (!reconcilesUnderTheLock(report.get())) {
      String trigger =
          new RunSebPendingTransactionReportReconciliationRequested(event.reportDate())
              .jobTriggerName();
      log.info(
          "SEB pending transactions report reconciliation deferred, its lock is held:"
              + " reportDate={}, trigger={}",
          event.reportDate(),
          trigger);
      jobTriggerQueue.enqueueUnlessPending(trigger);
    }
  }

  @EventListener
  public void onReportReconciliationRequested(
      RunSebPendingTransactionReportReconciliationRequested request) {
    Optional<InvestmentReport> report =
        reportService.getReport(SEB, PENDING_TRANSACTIONS, request.reportDate());
    if (report.isEmpty()) {
      log.warn(
          "SEB pending transactions report for a deferred reconciliation not found: reportDate={}",
          request.reportDate());
      return;
    }
    if (!reconcilesUnderTheLock(report.get())) {
      log.debug(
          "SEB pending transactions report reconciliation still waits for its lock: reportDate={}",
          request.reportDate());
      throw new SebReconciliationAlreadyRunningException(SebReconciliationLock.NAME);
    }
  }

  private boolean reconcilesUnderTheLock(InvestmentReport report) {
    try {
      return reconciliationLock.runIfFree(
          stillHeld -> reconciliationService.reconcile(report, stillHeld));
    } catch (SebReconciliationLockLostException e) {
      log.warn(
          "SEB pending transactions report reconciliation lost its lock, rolled back:"
              + " reportDate={}",
          report.getReportDate(),
          e);
      return false;
    }
  }
}
