package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SebPendingRowReconciler {

  private final SebPendingTransactionMatcher matcher;
  private final SebPendingTransactionComplexMatcher complexMatcher;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionOrderRepository orderRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ReconciliationAuditRecorder auditRecorder;
  private final SebMatchedRowProcessor matchedRowProcessor;

  RowOutcome reconcileRow(
      SebPendingTransactionRow row,
      LocalDate reportDate,
      LocalDate asOfDate,
      TransactionMatchingProperties matchingProperties,
      Set<Long> presentOrderIds) {
    presentOrderIds.addAll(referencedOrderIds(row));
    Optional<TransactionOrder> orderOpt = matchOrder(row, matchingProperties);
    if (orderOpt.isEmpty()) {
      handleUnmatchedRow(row, reportDate, matchingProperties);
      return RowOutcome.UNMATCHED;
    }
    TransactionOrder order = orderOpt.get();
    presentOrderIds.add(order.getId());
    return processMatchedRow(order, row, reportDate, asOfDate, matchingProperties);
  }

  private Optional<TransactionOrder> matchOrder(
      SebPendingTransactionRow row, TransactionMatchingProperties matchingProperties) {
    Optional<TransactionOrder> orderOpt = matcher.match(row);
    if (orderOpt.isEmpty()) {
      orderOpt = matchByBrokerRef(row);
    }
    if (orderOpt.isEmpty()) {
      orderOpt = complexMatcher.match(row, matchingProperties);
    }
    return orderOpt;
  }

  private void handleUnmatchedRow(
      SebPendingTransactionRow row,
      LocalDate reportDate,
      TransactionMatchingProperties matchingProperties) {
    Optional<QuantityAmountMismatchEvent> nearMiss =
        complexMatcher.findNearMiss(row, matchingProperties);
    if (nearMiss.isPresent()) {
      reportMismatch(nearMiss.get().withReportDate(reportDate), row);
      return;
    }
    log.info(
        "Unmatched pending transaction: clientRef={}, ourRef={}, isin={}, reportDate={}",
        row.clientRef(),
        row.ourRef(),
        row.isin(),
        reportDate);
    auditRecorder.recordUnmatched(row, reportDate);
  }

  private RowOutcome processMatchedRow(
      TransactionOrder order,
      SebPendingTransactionRow row,
      LocalDate reportDate,
      LocalDate asOfDate,
      TransactionMatchingProperties matchingProperties) {
    return matchedRowProcessor.process(order, row, reportDate, asOfDate, matchingProperties);
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
}
