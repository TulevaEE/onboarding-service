package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;

import ee.tuleva.onboarding.investment.event.ReportImportCompleted;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
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

  /**
   * Must run AFTER {@link SebPendingTransactionReconciliationListener} (order 0) so the matched
   * {@link TransactionExecution} rows are already persisted when we look them up.
   */
  static final int LISTENER_ORDER = 10;

  private final InvestmentReportService reportService;
  private final SebPendingTransactionExtractor extractor;
  private final SebPendingTransactionMatcher matcher;
  private final TransactionExecutionRepository executionRepository;
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
      Optional<TransactionOrder> orderOpt = matcher.match(row);
      if (orderOpt.isEmpty()) {
        continue;
      }
      TransactionOrder order = orderOpt.get();
      Optional<TransactionExecution> executionOpt =
          executionRepository.findByOrderId(order.getId());
      if (executionOpt.isEmpty()) {
        log.debug(
            "No execution yet for matched order, skipping NAV check: orderId={}, orderUuid={}",
            order.getId(),
            order.getOrderUuid());
        continue;
      }
      checkService.check(executionOpt.get(), order);
    }
  }
}
