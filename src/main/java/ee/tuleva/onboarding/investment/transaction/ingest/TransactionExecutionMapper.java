package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class TransactionExecutionMapper {

  static final String SOURCE_SEB_OOTEL = "SEB_OOTEL";
  static final String MODIFIED_BY_SEB_RECONCILIATION = "system:seb-reconciliation";

  TransactionExecution toExecution(
      SebPendingTransactionRow row, TransactionOrder order, LocalDate reportedDate) {
    TransactionExecution execution = applyTo(new TransactionExecution(), row, order);
    execution.setReportedDate(reportedDate);
    return execution;
  }

  TransactionExecution applyTo(
      TransactionExecution execution, SebPendingTransactionRow row, TransactionOrder order) {
    execution.setOrderId(order.getId());
    execution.setAggregatedOrderId(order.getOrderUuid());
    execution.setBrokerTransactionId(row.ourRef());
    execution.setExecutionTimestamp(row.tradeDate());
    execution.setExecutedQuantity(row.quantity());
    execution.setUnitPrice(row.price());
    execution.setTotalConsideration(row.total());
    execution.setSettlementAmount(row.settlementAmount());
    execution.setCommissionAmount(row.brokerFee());
    execution.setScheduledSettlementDate(row.settlementDate());
    execution.setSource(SOURCE_SEB_OOTEL);
    execution.setModifiedBy(MODIFIED_BY_SEB_RECONCILIATION);
    return execution;
  }

  // Art 16: no silent alteration of a transaction record.
  Map<String, Object> mutableFieldsForDeltaAudit(TransactionExecution execution) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("brokerTransactionId", asString(execution.getBrokerTransactionId()));
    snapshot.put("executionTimestamp", asString(execution.getExecutionTimestamp()));
    snapshot.put("executedQuantity", asString(execution.getExecutedQuantity()));
    snapshot.put("unitPrice", asString(execution.getUnitPrice()));
    snapshot.put("totalConsideration", asString(execution.getTotalConsideration()));
    snapshot.put("settlementAmount", asString(execution.getSettlementAmount()));
    snapshot.put("commissionAmount", asString(execution.getCommissionAmount()));
    snapshot.put("scheduledSettlementDate", asString(execution.getScheduledSettlementDate()));
    return snapshot;
  }

  private static String asString(Object value) {
    return value == null ? null : value.toString();
  }
}
