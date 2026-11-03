package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
import ee.tuleva.onboarding.investment.report.SebReportAsOfDate;
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
import java.util.function.Predicate;
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

  @RequiredArgsConstructor
  private enum SettlementByAbsence {
    ON_LATEST_REPORT_ONLY(execution -> true),
    ON_THIS_REPORT_AS_OF_ITS_DATE(execution -> true);

    private final Predicate<TransactionExecution> countsAsListed;
  }

  private final SebPendingTransactionExtractor extractor;
  private final QuantityAmountValidator quantityAmountValidator;
  private final TransactionMatchingPolicy matchingPolicy;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionOrderRepository orderRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final SebReportAsOfDate sebReportAsOfDate;
  private final ReconciliationAuditRecorder auditRecorder;
  private final TransactionSettlementRepository settlementRepository;
  private final TransactionSettlementService settlementService;
  private final InvestmentReportService reportService;
  private final SebPendingRowReconciler rowReconciler;

  @Value("${transaction-registry.settlement-check.scan-lookback-days:60}")
  private int scanLookbackDays = 60;

  @Transactional
  public void reconcile(InvestmentReport report) {
    reconcile(report, eventPublisher::publishEvent, SettlementByAbsence.ON_LATEST_REPORT_ONLY);
  }

  @Transactional
  SettlementCheck replay(InvestmentReport report, ReconciliationAlerts alerts) {
    return reconcile(report, alerts, SettlementByAbsence.ON_THIS_REPORT_AS_OF_ITS_DATE);
  }

  private SettlementCheck reconcile(
      InvestmentReport report,
      ReconciliationAlerts alerts,
      SettlementByAbsence settlementByAbsence) {
    LocalDate reportDate = report.getReportDate();
    LocalDate asOfDate =
        sebReportAsOfDate.resolveOrFallBackToReportDate(
            PENDING_TRANSACTIONS, reportDate, report.getMetadata(), report.getRawData());

    SebPendingTransactionExtractor.ExtractionResult extraction =
        extractor.extractWithDiagnostics(report);
    List<SebPendingTransactionRow> rows = extraction.rows();
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
              row, reportDate, asOfDate, matchingProperties, presentOrderIds, alerts);
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

    return detectSettlementsByAbsence(
        reportDate,
        asOfDate,
        rows.size(),
        matched,
        presentOrderIds,
        extraction.isComplete(),
        alerts,
        settlementByAbsence);
  }

  private SettlementCheck detectSettlementsByAbsence(
      LocalDate reportDate,
      LocalDate asOfDate,
      int rowCount,
      int matched,
      Set<Long> presentOrderIds,
      boolean reportComplete,
      ReconciliationAlerts alerts,
      SettlementByAbsence settlementByAbsence) {
    if (rowCount == 0) {
      log.info("Skipping settlement detection on empty report: reportDate={}", reportDate);
      return SettlementCheck.SKIPPED_EMPTY_REPORT;
    }
    if (matched == 0) {
      log.warn(
          "Skipping settlement detection, no rows matched known orders: reportDate={},"
              + " rowCount={}",
          reportDate,
          rowCount);
      return SettlementCheck.SKIPPED_NO_MATCHED_ROW;
    }
    if (!reportComplete) {
      log.warn(
          "Skipping settlement detection, report had malformed rows: reportDate={}", reportDate);
      return SettlementCheck.SKIPPED_MALFORMED_ROWS;
    }
    if (settlementByAbsence == SettlementByAbsence.ON_LATEST_REPORT_ONLY
        && !isLatestReport(reportDate)) {
      log.info("Skipping settlement detection on non-latest report: reportDate={}", reportDate);
      return SettlementCheck.SKIPPED_NOT_LATEST_REPORT;
    }
    if (isPossibleTruncation(reportDate, rowCount, alerts)) {
      return SettlementCheck.SKIPPED_POSSIBLE_TRUNCATION;
    }
    Instant since = reportDate.minusDays(scanLookbackDays).atStartOfDay(ZoneOffset.UTC).toInstant();
    orderRepository
        .findByOrderStatusInAndOrderTimestampSince(List.of(OrderStatus.EXECUTED), since)
        .stream()
        .filter(order -> order.getOrderVenue() == OrderVenue.SEB)
        .filter(order -> !presentOrderIds.contains(order.getId()))
        .filter(order -> !settlementRepository.existsByOrderId(order.getId()))
        .filter(order -> executedBefore(order, reportDate, asOfDate, settlementByAbsence))
        .forEach(order -> settleByAbsence(order, reportDate, asOfDate, settlementByAbsence));
    return SettlementCheck.CHECKED;
  }

  private boolean isLatestReport(LocalDate reportDate) {
    return reportService
        .getLatestReport(SEB, PENDING_TRANSACTIONS)
        .map(InvestmentReport::getReportDate)
        .map(latest -> !reportDate.isBefore(latest))
        .orElse(true);
  }

  private boolean isPossibleTruncation(
      LocalDate reportDate, int rowCount, ReconciliationAlerts alerts) {
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
    alerts.raise(
        new PossibleReportTruncationEvent(reportDate, rowCount, priorReportDate, priorRowCount));
    return true;
  }

  private boolean executedBefore(
      TransactionOrder order,
      LocalDate reportDate,
      LocalDate asOfDate,
      SettlementByAbsence settlementByAbsence) {
    return executionsListedBy(order, asOfDate, settlementByAbsence).stream()
        .map(TransactionExecution::getExecutionTimestamp)
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder())
        .map(timestamp -> timestamp.atZone(ZoneOffset.UTC).toLocalDate().isBefore(reportDate))
        .orElse(false);
  }

  private List<TransactionExecution> executionsListedBy(
      TransactionOrder order, LocalDate asOfDate, SettlementByAbsence settlementByAbsence) {
    return executionRepository.findAllByOrderId(order.getId()).stream()
        .filter(settlementByAbsence.countsAsListed)
        .filter(execution -> isListedBy(execution, asOfDate))
        .toList();
  }

  private static boolean isListedBy(TransactionExecution execution, LocalDate asOfDate) {
    LocalDate reportedDate = execution.getReportedDate();
    return reportedDate == null || !reportedDate.isAfter(asOfDate);
  }

  private void settleByAbsence(
      TransactionOrder order,
      LocalDate reportDate,
      LocalDate asOfDate,
      SettlementByAbsence settlementByAbsence) {
    List<TransactionExecution> executions =
        executionsListedBy(order, asOfDate, settlementByAbsence);
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
}
