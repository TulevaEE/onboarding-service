package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import org.springframework.stereotype.Component;

@Component
class TransactionExecutionMapper {

  static final String SOURCE_SEB_OOTEL = "SEB_OOTEL";

  TransactionExecution toExecution(SebPendingTransactionRow row, TransactionOrder order) {
    return TransactionExecution.builder()
        .orderId(order.getId())
        .brokerTransactionId(row.ourRef())
        .executionTimestamp(row.tradeDate())
        .executedQuantity(row.quantity())
        .unitPrice(row.price())
        .totalConsideration(row.total())
        .commissionAmount(row.brokerFee())
        .actualSettlementDate(row.settlementDate())
        .source(SOURCE_SEB_OOTEL)
        .build();
  }

  void applyTo(
      TransactionExecution existing, SebPendingTransactionRow row, TransactionOrder order) {
    existing.setOrderId(order.getId());
    existing.setBrokerTransactionId(row.ourRef());
    existing.setExecutionTimestamp(row.tradeDate());
    existing.setExecutedQuantity(row.quantity());
    existing.setUnitPrice(row.price());
    existing.setTotalConsideration(row.total());
    existing.setCommissionAmount(row.brokerFee());
    existing.setActualSettlementDate(row.settlementDate());
    existing.setSource(SOURCE_SEB_OOTEL);
  }
}
