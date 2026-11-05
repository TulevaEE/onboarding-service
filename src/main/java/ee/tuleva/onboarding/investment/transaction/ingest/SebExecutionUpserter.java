package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.HistoricalRegistryImportService.SOURCE_HISTORICAL_IMPORT;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SebExecutionUpserter {

  private final TransactionExecutionMapper executionMapper;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionOrderRepository orderRepository;
  private final ReconciliationAuditRecorder auditRecorder;

  boolean upsert(
      SebPendingTransactionRow row,
      TransactionOrder order,
      LocalDate reportDate,
      LocalDate asOfDate,
      @Nullable BigDecimal sebReportedQuantity,
      ReconciliationMode mode) {
    if (wouldOrphanExistingExecution(row, order)) {
      return false;
    }
    Optional<TransactionExecution> existing =
        executionRepository.findByBrokerTransactionId(
            requireNonNull(row.ourRef(), "Missing ourRef: orderId=" + order.getId()));
    if (existing.isPresent()) {
      TransactionExecution execution = existing.get();
      Map<String, Object> before = executionMapper.mutableFieldsForDeltaAudit(execution);
      Optional<LocalDate> restamped = restampedReportedDate(mode, execution, asOfDate);
      executionMapper.applyTo(execution, row, order);
      restamped.ifPresent(execution::setReportedDate);
      executionRepository.save(execution);
      Map<String, Object> after = executionMapper.mutableFieldsForDeltaAudit(execution);
      if (!before.equals(after)) {
        auditRecorder.recordExecutionUpdated(
            order, row, reportDate, before, after, sebReportedQuantity);
      }
    } else {
      executionRepository.save(executionMapper.toExecution(row, order, asOfDate));
      auditRecorder.recordExecutionMatched(order, row, reportDate, sebReportedQuantity);
    }

    order.setOrderStatus(OrderStatus.EXECUTED);
    orderRepository.save(order);
    return true;
  }

  private static Optional<LocalDate> restampedReportedDate(
      ReconciliationMode mode, TransactionExecution existing, LocalDate asOfDate) {
    if (mode == ReconciliationMode.LIVE) {
      return Optional.empty();
    }
    LocalDate reportedDate = existing.getReportedDate();
    if (SOURCE_HISTORICAL_IMPORT.equals(existing.getSource())
        || reportedDate == null
        || reportedDate.isAfter(asOfDate)) {
      return Optional.of(asOfDate);
    }
    return Optional.of(reportedDate);
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

  private Optional<TransactionExecution> uniqueExecutionByBrokerRef(String brokerRef) {
    var matches = executionRepository.findAllByBrokerTransactionId(brokerRef);
    if (matches.size() > 1) {
      log.error(
          "Refusing ambiguous broker-ref match: brokerTransactionId={}, executionCount={}",
          brokerRef,
          matches.size());
      return Optional.empty();
    }
    return matches.stream().findFirst();
  }
}
