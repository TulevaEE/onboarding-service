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
import ee.tuleva.onboarding.investment.transaction.TransactionSettlement;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
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
  private final SebReportAsOfDate sebReportAsOfDate;
  private final ReconciliationAuditRecorder auditRecorder;
  private final TransactionSettlementRepository settlementRepository;
  private final TransactionSettlementService settlementService;
  private final InvestmentReportService reportService;
  private final SebPendingRowReconciler rowReconciler;

  @Value("${transaction-registry.settlement-check.scan-lookback-days:60}")
  private int scanLookbackDays = 60;

  @Transactional
  public void reconcile(InvestmentReport report, BooleanSupplier stillHoldsTheLock) {
    reconcile(
        report,
        new ReconciliationRun(
            ReconciliationMode.LIVE, eventPublisher::publishEvent, new SebReplayListings()),
        stillHoldsTheLock);
  }

  @Transactional
  SettlementCheck replay(
      InvestmentReport report,
      ReconciliationAlerts alerts,
      SebReplayListings listings,
      BooleanSupplier stillHoldsTheLock) {
    return reconcile(
        report,
        new ReconciliationRun(ReconciliationMode.REPLAY, alerts, listings),
        stillHoldsTheLock);
  }

  void noteListedBy(InvestmentReport report, SebReplayListings listings) {
    listings.addListedBy(extractor.extract(report), asOfDateOf(report));
  }

  private LocalDate asOfDateOf(InvestmentReport report) {
    return sebReportAsOfDate.resolveOrFallBackToReportDate(
        PENDING_TRANSACTIONS, report.getReportDate(), report.getMetadata(), report.getRawData());
  }

  private SettlementCheck reconcile(
      InvestmentReport report, ReconciliationRun run, BooleanSupplier stillHoldsTheLock) {
    LocalDate reportDate = report.getReportDate();
    LocalDate asOfDate = asOfDateOf(report);

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
              row, reportDate, asOfDate, matchingProperties, presentOrderIds, run);
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

    SettlementCheck check =
        detectSettlementsByAbsence(
            reportDate, rows.size(), matched, presentOrderIds, extraction.isComplete(), run);
    if (!stillHoldsTheLock.getAsBoolean()) {
      throw new SebReconciliationLockLostException(reportDate);
    }
    run.listings().addListedBy(rows, asOfDate);
    return check;
  }

  private SettlementCheck detectSettlementsByAbsence(
      LocalDate reportDate,
      int rowCount,
      int matched,
      Set<Long> presentOrderIds,
      boolean reportComplete,
      ReconciliationRun run) {
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
    if (run.mode() == ReconciliationMode.LIVE && !isLatestReport(reportDate)) {
      log.info("Skipping settlement detection on non-latest report: reportDate={}", reportDate);
      return SettlementCheck.SKIPPED_NOT_LATEST_REPORT;
    }
    if (isPossibleTruncation(reportDate, rowCount, run)) {
      return SettlementCheck.SKIPPED_POSSIBLE_TRUNCATION;
    }
    Instant since = reportDate.minusDays(scanLookbackDays).atStartOfDay(ZoneOffset.UTC).toInstant();
    orderRepository
        .findByOrderStatusInAndOrderTimestampSince(List.of(OrderStatus.EXECUTED), since)
        .stream()
        .filter(order -> order.getOrderVenue() == OrderVenue.SEB)
        .filter(order -> !presentOrderIds.contains(order.getId()))
        .filter(order -> !settlementRepository.existsByOrderId(order.getId()))
        .filter(order -> absenceMaySettle(order, reportDate, run))
        .forEach(order -> settleByAbsence(order, reportDate, run));
    if (run.mode() == ReconciliationMode.REPLAY) {
      moveLaterSettlementsBackToThisAbsence(reportDate, since, presentOrderIds, run);
    }
    return SettlementCheck.CHECKED;
  }

  private void moveLaterSettlementsBackToThisAbsence(
      LocalDate reportDate, Instant since, Set<Long> presentOrderIds, ReconciliationRun run) {
    orderRepository
        .findByOrderStatusInAndOrderTimestampSince(List.of(OrderStatus.SETTLED), since)
        .stream()
        .filter(order -> order.getOrderVenue() == OrderVenue.SEB)
        .filter(order -> !presentOrderIds.contains(order.getId()))
        .forEach(
            order ->
                settlementRepository
                    .findByOrderId(order.getId())
                    .filter(settlement -> mayBeRedatedTo(settlement, reportDate))
                    .filter(settlement -> absenceSettlesInAReplay(order, reportDate, run))
                    .ifPresent(settlement -> redateByAbsence(order, settlement, reportDate)));
  }

  private static boolean mayBeRedatedTo(TransactionSettlement settlement, LocalDate reportDate) {
    return settlement.getReportDate().isAfter(reportDate);
  }

  private boolean absenceMaySettle(
      TransactionOrder order, LocalDate reportDate, ReconciliationRun run) {
    return switch (run.mode()) {
      case LIVE -> executedBefore(order, reportDate, run);
      case REPLAY -> absenceSettlesInAReplay(order, reportDate, run);
    };
  }

  private boolean absenceSettlesInAReplay(
      TransactionOrder order, LocalDate reportDate, ReconciliationRun run) {
    List<TransactionExecution> listed = executionsListedBy(order, run);
    return tradedBefore(listed, reportDate)
        && filledAsLastListed(order, listed, reportDate, run.listings())
        && !quantityAmountValidator.isShortFill(order, listed, matchingPolicy.current());
  }

  private boolean filledAsLastListed(
      TransactionOrder order,
      List<TransactionExecution> listed,
      LocalDate reportDate,
      SebReplayListings listings) {
    Optional<TransactionExecution> differing =
        listed.stream()
            .filter(
                execution ->
                    listings
                        .lastListing(execution.getBrokerTransactionId())
                        .filter(
                            listing ->
                                quantityAmountValidator.fillAsListed(order, execution, listing))
                        .isEmpty())
            .findFirst();
    differing.ifPresent(
        execution ->
            log.warn(
                "Absence does not settle the order in the replay, its stored fill differs from"
                    + " SEB's last listing before this report: orderId={}, reportDate={},"
                    + " brokerRef={}",
                order.getId(),
                reportDate,
                execution.getBrokerTransactionId()));
    return differing.isEmpty();
  }

  private void redateByAbsence(
      TransactionOrder order, TransactionSettlement settlement, LocalDate reportDate) {
    log.info(
        "Settlement moved back to an earlier absence from the pending report: orderId={},"
            + " fromReportDate={}, toReportDate={}",
        order.getId(),
        settlement.getReportDate(),
        reportDate);
    Map<String, Object> before = ReconciliationAuditRecorder.settlementSnapshot(settlement);
    settlementService.redate(settlement, reportDate);
    auditRecorder.recordSettlementRedated(order, reportDate, before, settlement);
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
      TransactionOrder order, LocalDate reportDate, ReconciliationRun run) {
    return tradedBefore(executionsListedBy(order, run), reportDate);
  }

  private static boolean tradedBefore(List<TransactionExecution> executions, LocalDate reportDate) {
    return executions.stream()
        .map(TransactionExecution::getExecutionTimestamp)
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder())
        .map(timestamp -> timestamp.atZone(ZoneOffset.UTC).toLocalDate().isBefore(reportDate))
        .orElse(false);
  }

  private List<TransactionExecution> executionsListedBy(
      TransactionOrder order, ReconciliationRun run) {
    return executionRepository.findAllByOrderId(order.getId()).stream()
        .filter(execution -> countsAsListed(run, execution))
        .toList();
  }

  private static boolean countsAsListed(ReconciliationRun run, TransactionExecution execution) {
    return switch (run.mode()) {
      case LIVE -> true;
      case REPLAY -> run.listings().listedBefore(execution.getBrokerTransactionId());
    };
  }

  private void settleByAbsence(
      TransactionOrder order, LocalDate reportDate, ReconciliationRun run) {
    List<TransactionExecution> executions = executionsListedBy(order, run);
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
