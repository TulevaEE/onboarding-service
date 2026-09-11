package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record ParsedRow(
    int rowNumber,
    String orderId,
    UUID orderUuid,
    @Nullable String brokerTransactionId,
    TulevaFund fund,
    String instrumentIsin,
    TransactionType transactionType,
    InstrumentType instrumentType,
    @Nullable BigDecimal orderAmount,
    @Nullable BigDecimal orderQuantity,
    @Nullable Instant orderTimestamp,
    OrderStatus orderStatus,
    @Nullable LocalDate expectedSettlementDate,
    @Nullable String comment,
    @Nullable Instant executionTimestamp,
    @Nullable BigDecimal executedQuantity,
    @Nullable BigDecimal unitPrice,
    @Nullable BigDecimal totalConsideration,
    @Nullable BigDecimal settlementAmount,
    @Nullable BigDecimal commissionAmount,
    @Nullable LocalDate actualSettlementDate) {

  boolean hasExecutionData() {
    return executionTimestamp != null
        || executedQuantity != null
        || unitPrice != null
        || totalConsideration != null;
  }

  boolean requiresExecutedQuantity() {
    return instrumentType == InstrumentType.ETF || transactionType == TransactionType.SELL;
  }

  boolean isFundSubscription() {
    return instrumentType == InstrumentType.FUND && transactionType == TransactionType.BUY;
  }

  LocalDate settlementReportDate() {
    if (actualSettlementDate != null) {
      return actualSettlementDate;
    }
    if (expectedSettlementDate != null) {
      return expectedSettlementDate;
    }
    throw new RowParseException(
        "Settled row missing both actual and expected settlement date: orderId=" + orderId);
  }

  BigDecimal totalAmount() {
    if (totalConsideration != null) {
      return totalConsideration;
    }
    return orderAmount != null ? orderAmount : BigDecimal.ZERO;
  }
}
