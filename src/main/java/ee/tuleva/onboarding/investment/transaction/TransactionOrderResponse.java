package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record TransactionOrderResponse(
    Long id,
    String instrumentIsin,
    TransactionType transactionType,
    InstrumentType instrumentType,
    @Nullable BigDecimal orderAmount,
    @Nullable BigDecimal orderQuantity,
    OrderVenue orderVenue,
    OrderStatus orderStatus,
    UUID orderUuid,
    @Nullable LocalDate expectedSettlementDate,
    @Nullable String comment) {

  static TransactionOrderResponse from(TransactionOrder order) {
    return new TransactionOrderResponse(
        order.getId(),
        order.getInstrumentIsin(),
        order.getTransactionType(),
        order.getInstrumentType(),
        order.getOrderAmount(),
        order.getOrderQuantity(),
        order.getOrderVenue(),
        order.getOrderStatus(),
        order.getOrderUuid(),
        order.getExpectedSettlementDate(),
        order.getComment());
  }
}
