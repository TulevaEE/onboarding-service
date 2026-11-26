package ee.tuleva.onboarding.investment.transaction.export;

import ee.tuleva.onboarding.investment.transaction.OrderSender;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record OrderDay(
    List<Long> batchIds,
    LocalDate tradeDate,
    Instant sentAt,
    OrderSender sender,
    List<TransactionOrder> orders,
    Map<ExportFile, byte[]> brokerFiles) {

  public OrderDay {
    batchIds = List.copyOf(batchIds);
    orders = List.copyOf(orders);
    brokerFiles = Map.copyOf(brokerFiles);
  }

  String emailKey(OrderEmail email) {
    return email.name()
        + ":"
        + batchIds.stream().map(String::valueOf).collect(Collectors.joining(","));
  }
}
