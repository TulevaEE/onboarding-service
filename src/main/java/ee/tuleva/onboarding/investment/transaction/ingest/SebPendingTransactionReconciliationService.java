package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlement;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
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
  private final TransactionExecutionMapper executionMapper;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionOrderRepository orderRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ReconciliationAuditRecorder auditRecorder;
  private final TransactionSettlementRepository settlementRepository;
  private final TransactionSettlementService settlementService;

  @Transactional
  public void reconcile(InvestmentReport report) {
    var rows = extractor.extract(report);
    LocalDate reportDate = report.getReportDate();
    log.info(
        "Reconciling SEB pending transactions: reportDate={}, rowCount={}",
        reportDate,
        rows.size());

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
        orderOpt = complexMatcher.match(row);
      }
      if (orderOpt.isEmpty()) {
        Optional<QuantityAmountMismatchEvent> nearMiss = complexMatcher.findNearMiss(row);
        if (nearMiss.isPresent()) {
          reportMismatch(nearMiss.get().withReportDate(reportDate), row);
          unmatched++;
          continue;
        }
        // Unmatched rows are surfaced read-only by the daily SettlementCheckJob digest,
        // not alerted per-row here.
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
      Optional<TransactionSettlement> settlement =
          settlementRepository.findByOrderId(order.getId());
      if (settlement.isPresent()) {
        matched++;
        handleSettledOrderReappearance(order, settlement.get(), row, reportDate);
        continue;
      }
      quantityAmountValidator
          .validate(order, row)
          .ifPresent(mismatch -> reportMismatch(mismatch.withReportDate(reportDate), row));
      if (upsert(row, order, reportDate)) {
        matched++;
      }
    }

    log.info(
        "Reconciliation completed: reportDate={}, matched={}, unmatched={}",
        reportDate,
        matched,
        unmatched);

    detectSettlementsByAbsence(reportDate, rows.size(), matched, presentOrderIds);
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
    return executionRepository
        .findByBrokerTransactionId(row.ourRef())
        .map(TransactionExecution::getOrderId)
        .flatMap(orderRepository::findById);
  }

  private boolean upsert(
      SebPendingTransactionRow row, TransactionOrder order, LocalDate reportDate) {
    if (wouldOrphanExistingExecution(row, order)) {
      return false;
    }
    Optional<TransactionExecution> existing = executionRepository.findByOrderId(order.getId());
    TransactionExecution execution =
        existing
            .map(
                e -> {
                  executionMapper.applyTo(e, row, order);
                  return e;
                })
            .orElseGet(() -> executionMapper.toExecution(row, order));
    executionRepository.save(execution);
    if (existing.isEmpty()) {
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
      LocalDate reportDate, int rowCount, int matched, Set<Long> presentOrderIds) {
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
    orderRepository.findByOrderStatusIn(List.of(OrderStatus.EXECUTED)).stream()
        .filter(order -> order.getOrderVenue() == OrderVenue.SEB)
        .filter(order -> !presentOrderIds.contains(order.getId()))
        .filter(order -> !settlementRepository.existsByOrderId(order.getId()))
        .filter(order -> executedBefore(order, reportDate))
        .forEach(order -> settleByAbsence(order, reportDate));
  }

  private boolean executedBefore(TransactionOrder order, LocalDate reportDate) {
    return executionRepository
        .findByOrderId(order.getId())
        .map(TransactionExecution::getExecutionTimestamp)
        .map(timestamp -> timestamp.atZone(ZoneOffset.UTC).toLocalDate().isBefore(reportDate))
        .orElse(false);
  }

  private void settleByAbsence(TransactionOrder order, LocalDate reportDate) {
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
      executionRepository
          .findByBrokerTransactionId(row.ourRef())
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
    Optional<TransactionExecution> byBrokerId =
        executionRepository.findByBrokerTransactionId(row.ourRef());
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
