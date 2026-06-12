package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import org.springframework.stereotype.Component;

@Component
class TransactionExecutionMapper {

  static final String SOURCE_SEB_OOTEL = "SEB_OOTEL";
  static final String MODIFIED_BY_SEB_RECONCILIATION = "system:seb-reconciliation";

  TransactionExecution toExecution(SebPendingTransactionRow row, TransactionOrder order) {
    return applyTo(new TransactionExecution(), row, order);
  }

  TransactionExecution applyTo(
      TransactionExecution execution, SebPendingTransactionRow row, TransactionOrder order) {
    execution.setOrderId(order.getId());
    execution.setBrokerTransactionId(row.ourRef());
    execution.setExecutionTimestamp(row.tradeDate());
    execution.setExecutedQuantity(row.quantity());
    execution.setUnitPrice(row.price());
    execution.setTotalConsideration(row.total());
    execution.setSettlementAmount(row.settlementAmount());
    execution.setCommissionAmount(row.brokerFee());
    execution.setActualSettlementDate(row.settlementDate());
    execution.setSource(SOURCE_SEB_OOTEL);
    execution.setModifiedBy(MODIFIED_BY_SEB_RECONCILIATION);
    return execution;
  }
}
