package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.report.SebReportHeaders;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SebPendingTransactionReconciliationService {

  private static final double TRUNCATION_ROW_DROP_RATIO = 0.5;

  private final SebPendingTransactionExtractor extractor;
  private final QuantityAmountValidator quantityAmountValidator;
  private final TransactionMatchingPolicy matchingPolicy;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionOrderRepository orderRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ReconciliationAuditRecorder auditRecorder;
  private final TransactionSettlementRepository settlementRepository;
  private final TransactionSettlementService settlementService;
  private final InvestmentReportService reportService;
  private final SebPendingRowReconciler rowReconciler;

  @Value("${transaction-registry.settlement-check.scan-lookback-days:60}")
  private int scanLookbackDays = 60;

  @Transactional
  public void reconcile(InvestmentReport report) {
    SebPendingTransactionExtractor.ExtractionResult extraction =
        extractor.extractWithDiagnostics(report);
    List<SebPendingTransactionRow> rows = extraction.rows();
    LocalDate reportDate = report.getReportDate();
    LocalDate asOfDate = asOfDate(report);
    TransactionMatchingProperties matchingProperties = matchingPolicy.current();
    log.info(
        "Reconciling SEB pending transactions: reportDate={}, rowCount={}, malformedCount={}",
        reportDate,
        rows.size(),
        extraction.malformedCount());

    int matched = 0;
    int unmatched = 0;
    Set<Long> presentOrderIds = new HashSet<>();
    for (SebPendingTransactionRow row : rows) {
      RowOutcome outcome =
          rowReconciler.reconcileRow(
              row, reportDate, asOfDate, matchingProperties, presentOrderIds);
      switch (outcome) {
        case MATCHED -> matched++;
        case UNMATCHED -> unmatched++;
        case SKIPPED -> {}
      }
    }

    log.info(
        "Reconciliation completed: reportDate={}, matched={}, unmatched={}",
        reportDate,
        matched,
        unmatched);

    detectSettlementsByAbsence(
        reportDate, rows.size(), matched, presentOrderIds, extraction.isComplete());
  }

  private void detectSettlementsByAbsence(
      LocalDate reportDate,
      int rowCount,
      int matched,
      Set<Long> presentOrderIds,
      boolean reportComplete) {
    if (rowCount == 0) {
      log.info("Skipping settlement detection on empty report: reportDate={}", reportDate);
      return;
    }
    if (matched == 0) {
      log.warn(
          "Skipping settlement detection, no rows matched known orders: reportDate={},"
              + " rowCount={}",
          reportDate,
          rowCount);
      return;
    }
    if (!reportComplete) {
      log.warn(
          "Skipping settlement detection, report had malformed rows: reportDate={}", reportDate);
      return;
    }
    if (!isLatestReport(reportDate)) {
      log.info("Skipping settlement detection on non-latest report: reportDate={}", reportDate);
      return;
    }
    if (isPossibleTruncation(reportDate, rowCount)) {
      return;
    }
    Instant since = reportDate.minusDays(scanLookbackDays).atStartOfDay(ZoneOffset.UTC).toInstant();
    orderRepository
        .findByOrderStatusInAndOrderTimestampSince(List.of(OrderStatus.EXECUTED), since)
        .stream()
        .filter(order -> order.getOrderVenue() == OrderVenue.SEB)
        .filter(order -> !presentOrderIds.contains(order.getId()))
        .filter(order -> !settlementRepository.existsByOrderId(order.getId()))
        .filter(order -> executedBefore(order, reportDate))
        .forEach(order -> settleByAbsence(order, reportDate));
  }

  private boolean isLatestReport(LocalDate reportDate) {
    return reportService
        .getLatestReport(SEB, PENDING_TRANSACTIONS)
        .map(InvestmentReport::getReportDate)
        .map(latest -> !reportDate.isBefore(latest))
        .orElse(true);
  }

  private boolean isPossibleTruncation(LocalDate reportDate, int rowCount) {
    Optional<InvestmentReport> priorReport =
        reportService.getPriorReport(SEB, PENDING_TRANSACTIONS, reportDate);
    if (priorReport.isEmpty()) {
      log.info(
          "Skipping truncation comparison, no prior SEB pending report found: reportDate={}",
          reportDate);
      return false;
    }
    int priorRowCount = extractor.extractWithDiagnostics(priorReport.get()).rows().size();
    boolean truncated = rowCount < priorRowCount * (1 - TRUNCATION_ROW_DROP_RATIO);
    if (!truncated) {
      return false;
    }
    LocalDate priorReportDate = priorReport.get().getReportDate();
    log.warn(
        "Possible SEB pending report truncation, skipping settlement by absence: reportDate={},"
            + " rowCount={}, priorReportDate={}, priorRowCount={}",
        reportDate,
        rowCount,
        priorReportDate,
        priorRowCount);
    auditRecorder.recordPossibleReportTruncation(
        reportDate, rowCount, priorReportDate, priorRowCount);
    eventPublisher.publishEvent(
        new PossibleReportTruncationEvent(reportDate, rowCount, priorReportDate, priorRowCount));
    return true;
  }

  private boolean executedBefore(TransactionOrder order, LocalDate reportDate) {
    return latestExecutionTimestamp(order)
        .map(timestamp -> timestamp.atZone(ZoneOffset.UTC).toLocalDate().isBefore(reportDate))
        .orElse(false);
  }

  private Optional<Instant> latestExecutionTimestamp(TransactionOrder order) {
    return executionRepository.findAllByOrderId(order.getId()).stream()
        .map(TransactionExecution::getExecutionTimestamp)
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder());
  }

  private void settleByAbsence(TransactionOrder order, LocalDate reportDate) {
    List<TransactionExecution> executions = executionRepository.findAllByOrderId(order.getId());
    if (quantityAmountValidator.isShortFill(order, executions, matchingPolicy.current())) {
      log.error(
          "Order absent from pending report but not fully filled, leaving EXECUTED: orderId={},"
              + " reportDate={}, executionCount={}",
          order.getId(),
          reportDate,
          executions.size());
      return;
    }
    log.info(
        "Settlement detected by absence from pending report: orderId={}, reportDate={}",
        order.getId(),
        reportDate);
    settlementService.recordSettlement(order, reportDate);
    auditRecorder.recordSettlementDetected(order, reportDate);
  }

  private LocalDate asOfDate(InvestmentReport report) {
    LocalDate asOfDate = SebReportHeaders.asOfDate(report);
    if (asOfDate == null) {
      log.warn(
          "No 'As of' date in SEB pending transactions report, falling back to report date:"
              + " reportDate={}",
          report.getReportDate());
      return report.getReportDate();
    }
    return asOfDate;
  }
}
