package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record TransactionCommandResponse(
    Long id,
    TulevaFund fund,
    TransactionMode mode,
    LocalDate asOfDate,
    CommandStatus status,
    @Nullable String errorMessage,
    @Nullable Long batchId,
    List<TransactionOrderResponse> orders,
    @Nullable Map<String, Object> calculationSnapshot) {

  static TransactionCommandResponse from(
      TransactionCommand command,
      List<TransactionOrder> orders,
      @Nullable Map<String, Object> calculationSnapshot) {
    return new TransactionCommandResponse(
        command.getId(),
        command.getFund(),
        command.getMode(),
        command.getAsOfDate(),
        command.getStatus(),
        command.getErrorMessage(),
        command.getBatchId(),
        orders.stream().map(TransactionOrderResponse::from).toList(),
        calculationSnapshot);
  }
}
