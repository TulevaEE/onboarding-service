package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.report.InvestmentReportService;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SebPendingTransactionReconciliationService {

  private final SebPendingTransactionExtractor extractor;
  private final SebPendingTransactionMatcher matcher;
  private final SebPendingTransactionComplexMatcher complexMatcher;
  private final QuantityAmountValidator quantityAmountValidator;
  private final ExecutionPriceConsistencyChecker priceConsistencyChecker;
  private final TransactionMatchingPolicy matchingPolicy;
  private final TransactionExecutionMapper executionMapper;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionOrderRepository orderRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ReconciliationAuditRecorder auditRecorder;
  private final TransactionSettlementRepository settlementRepository;
  private final TransactionSettlementService settlementService;
  private final InvestmentReportService reportService;

  @Transactional
  public void reconcile(InvestmentReport report) {
    SebPendingTransactionExtractor.ExtractionResult extraction =
        extractor.extractWithDiagnostics(report);
    List<SebPendingTransactionRow> rows = extraction.rows();
    LocalDate reportDate = report.getReportDate();
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
      presentOrderIds.addAll(referencedOrderIds(row));
      Optional<TransactionOrder> orderOpt = matcher.match(row);
      if (orderOpt.isEmpty()) {
        orderOpt = matchByBrokerRef(row);
      }
      if (orderOpt.isEmpty()) {
        orderOpt = complexMatcher.match(row, matchingProperties);
      }
      if (orderOpt.isEmpty()) {
        Optional<QuantityAmountMismatchEvent> nearMiss =
            complexMatcher.findNearMiss(row, matchingProperties);
        if (nearMiss.isPresent()) {
          reportMismatch(nearMiss.get().withReportDate(reportDate), row);
          unmatched++;
          continue;
        }
        log.info(
            "Unmatched pending transaction: clientRef={}, ourRef={}, isin={}, reportDate={}",
            row.clientRef(),
            row.ourRef(),
            row.isin(),
            reportDate);
        auditRecorder.recordUnmatched(row, reportDate);
        unmatched++;
        continue;
      }
      TransactionOrder order = orderOpt.get();
      presentOrderIds.add(order.getId());
      if (!isRowConsistentWithOrder(order, row, reportDate)) {
        matched++;
        continue;
      }
      Optional<TransactionSettlement> settlement =
          settlementRepository.findByOrderId(order.getId());
      if (settlement.isPresent()) {
        matched++;
        handleSettledOrderReappearance(order, settlement.get(), row, reportDate);
        continue;
      }
      Optional<QuantityAmountMismatchEvent> mismatch =
          quantityAmountValidator.validateCumulative(
              order, row, executionRepository.findAllByOrderId(order.getId()), matchingProperties);
      if (mismatch.isPresent()) {
        reportMismatch(mismatch.get().withReportDate(reportDate), row);
        matched++;
        continue;
      }
      if (upsert(row, order, reportDate)) {
        matched++;
        checkPriceConsistency(order, reportDate, matchingProperties);
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

  private void checkPriceConsistency(
      TransactionOrder order, LocalDate reportDate, TransactionMatchingProperties properties) {
    List<TransactionExecution> executions = executionRepository.findAllByOrderId(order.getId());
    priceConsistencyChecker
        .check(order, executions, properties.executionPriceConsistencyTolerance())
        .ifPresent(
            event -> {
              log.error(
                  "Cross-piece price divergence: orderId={}, isin={}, min={}, max={}, spread={},"
                      + " tolerance={}, reportDate={}",
                  order.getId(),
                  order.getInstrumentIsin(),
                  event.minUnitPrice(),
                  event.maxUnitPrice(),
                  event.relativeSpread(),
                  event.tolerance(),
                  reportDate);
              eventPublisher.publishEvent(event.withReportDate(reportDate));
            });
  }

  private void reportMismatch(QuantityAmountMismatchEvent event, SebPendingTransactionRow row) {
    log.info(
        "Quantity/amount mismatch: clientRef={}, ourRef={}, isin={}, kind={}, expected={},"
            + " actual={}, reportDate={}",
        row.clientRef(),
        row.ourRef(),
        row.isin(),
        event.kind(),
        event.expected(),
        event.actual(),
        event.reportDate());
    auditRecorder.recordQuantityAmountMismatch(event);
    eventPublisher.publishEvent(event);
  }

  private Optional<TransactionOrder> matchByBrokerRef(SebPendingTransactionRow row) {
    if (row.ourRef() == null) {
      return Optional.empty();
    }
    return uniqueExecutionByBrokerRef(row.ourRef())
        .map(TransactionExecution::getOrderId)
        .flatMap(orderRepository::findById);
  }

  private Optional<TransactionExecution> uniqueExecutionByBrokerRef(String brokerRef) {
    List<TransactionExecution> matches =
        executionRepository.findAllByBrokerTransactionId(brokerRef);
    if (matches.size() > 1) {
      log.error(
          "Refusing ambiguous broker-ref match: brokerTransactionId={}, executionCount={}",
          brokerRef,
          matches.size());
      return Optional.empty();
    }
    return matches.stream().findFirst();
  }

  private boolean isRowConsistentWithOrder(
      TransactionOrder order, SebPendingTransactionRow row, LocalDate reportDate) {
    if (row.ourRef() == null) {
      log.error(
          "Matched SEB row has no Our ref, cannot record as a piece: orderId={}, clientRef={},"
              + " isin={}, reportDate={}",
          order.getId(),
          row.clientRef(),
          row.isin(),
          reportDate);
      return false;
    }
    if (!row.isin().equals(order.getInstrumentIsin()) || row.side() != order.getTransactionType()) {
      log.error(
          "Matched SEB row instrument/side does not match order: orderId={}, orderIsin={},"
              + " rowIsin={}, orderSide={}, rowSide={}, ourRef={}, reportDate={}",
          order.getId(),
          order.getInstrumentIsin(),
          row.isin(),
          order.getTransactionType(),
          row.side(),
          row.ourRef(),
          reportDate);
      return false;
    }
    return true;
  }

  private boolean upsert(
      SebPendingTransactionRow row, TransactionOrder order, LocalDate reportDate) {
    if (wouldOrphanExistingExecution(row, order)) {
      return false;
    }
    Optional<TransactionExecution> existing =
        executionRepository.findByBrokerTransactionId(row.ourRef());
    if (existing.isPresent()) {
      TransactionExecution execution = existing.get();
      Map<String, Object> before = executionMapper.snapshot(execution);
      executionMapper.applyTo(execution, row, order);
      executionRepository.save(execution);
      Map<String, Object> after = executionMapper.snapshot(execution);
      if (!before.equals(after)) {
        auditRecorder.recordExecutionUpdated(order, row, reportDate, before, after);
      }
    } else {
      executionRepository.save(executionMapper.toExecution(row, order));
      auditRecorder.recordExecutionMatched(order, row, reportDate);
    }

    order.setOrderStatus(OrderStatus.EXECUTED);
    orderRepository.save(order);
    return true;
  }

  private void handleSettledOrderReappearance(
      TransactionOrder order,
      TransactionSettlement settlement,
      SebPendingTransactionRow row,
      LocalDate reportDate) {
    if (!reportDate.isAfter(settlement.getReportDate())) {
      return;
    }
    log.error(
        "Settled order reappeared in pending report: orderId={}, settlementReportDate={},"
            + " reportDate={}, clientRef={}, ourRef={}",
        order.getId(),
        settlement.getReportDate(),
        reportDate,
        row.clientRef(),
        row.ourRef());
    auditRecorder.recordSettlementReappeared(order, settlement, row, reportDate);
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
    orderRepository.findByOrderStatusIn(List.of(OrderStatus.EXECUTED)).stream()
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

  private Set<Long> referencedOrderIds(SebPendingTransactionRow row) {
    Set<Long> orderIds = new HashSet<>();
    if (row.ourRef() != null) {
      uniqueExecutionByBrokerRef(row.ourRef())
          .map(TransactionExecution::getOrderId)
          .ifPresent(orderIds::add);
    }
    if (row.clientRef() != null) {
      orderRepository
          .findByOrderUuid(row.clientRef())
          .map(TransactionOrder::getId)
          .ifPresent(orderIds::add);
    }
    return orderIds;
  }

  private boolean wouldOrphanExistingExecution(
      SebPendingTransactionRow row, TransactionOrder order) {
    if (row.ourRef() == null) {
      return false;
    }
    Optional<TransactionExecution> byBrokerId = uniqueExecutionByBrokerRef(row.ourRef());
    if (byBrokerId.isEmpty()) {
      return false;
    }
    Long existingOrderId = byBrokerId.get().getOrderId();
    if (existingOrderId == null || existingOrderId.equals(order.getId())) {
      return false;
    }
    log.warn(
        "Refusing to re-link execution to different order: brokerTransactionId={},"
            + " existingOrderId={}, proposedOrderId={}, clientRef={}",
        row.ourRef(),
        existingOrderId,
        order.getId(),
        row.clientRef());
    return true;
  }
}
