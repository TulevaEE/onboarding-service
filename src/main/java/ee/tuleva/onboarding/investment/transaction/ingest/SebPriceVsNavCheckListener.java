package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;

import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(SebPriceVsNavCheckListener.LISTENER_ORDER)
@RequiredArgsConstructor
class SebPriceVsNavCheckListener {

  static final int LISTENER_ORDER = SebPendingTransactionReconciliationListener.LISTENER_ORDER + 10;

  private final InvestmentReportService reportService;
  private final SebPendingTransactionExtractor extractor;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionOrderRepository orderRepository;
  private final SebPriceVsNavCheckService checkService;

  @EventListener
  public void onReportImportCompleted(ReportImportCompleted event) {
    if (event.provider() != SEB || event.reportType() != PENDING_TRANSACTIONS) {
      return;
    }
    Optional<InvestmentReport> reportOpt =
        reportService.getReport(event.provider(), event.reportType(), event.reportDate());
    if (reportOpt.isEmpty()) {
      log.warn(
          "SEB pending transactions report not found for NAV check: reportDate={}",
          event.reportDate());
      return;
    }

    InvestmentReport report = reportOpt.get();
    var rows = extractor.extract(report);
    log.info(
        "Running SEB price-vs-NAV check: reportDate={}, rowCount={}",
        report.getReportDate(),
        rows.size());

    for (SebPendingTransactionRow row : rows) {
      checkRow(row);
    }
  }

  private void checkRow(SebPendingTransactionRow row) {
    if (row.ourRef() == null) {
      return;
    }
    // Find via brokerTransactionId so we cover both simple (UUID) and complex (fuzzy)
    // reconciliation linkages — by the time this listener fires, the reconciliation
    // listener has already persisted the execution row.
    Optional<TransactionExecution> executionOpt =
        executionRepository.findByBrokerTransactionId(row.ourRef());
    if (executionOpt.isEmpty()) {
      return;
    }
    TransactionExecution execution = executionOpt.get();
    if (execution.getOrderId() == null) {
      return;
    }
    Optional<TransactionOrder> orderOpt = orderRepository.findById(execution.getOrderId());
    if (orderOpt.isEmpty()) {
      log.warn(
          "Execution has orderId but order not found, skipping NAV check: executionId={},"
              + " orderId={}",
          execution.getId(),
          execution.getOrderId());
      return;
    }
    checkService.check(execution, orderOpt.get());
  }
}
