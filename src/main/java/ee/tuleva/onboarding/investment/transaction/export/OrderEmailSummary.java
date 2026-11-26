package ee.tuleva.onboarding.investment.transaction.export;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

record OrderEmailSummary(
    String key, OrderEmail email, int orderCount, int ordersWithoutAmount, BigDecimal amount) {

  static OrderEmailSummary of(OrderDay day, OrderEmail email) {
    List<TransactionOrder> orders = day.orders().stream().filter(email::carries).toList();
    List<BigDecimal> amounts =
        orders.stream().map(TransactionOrder::getOrderAmount).filter(Objects::nonNull).toList();
    return new OrderEmailSummary(
        day.emailKey(email),
        email,
        orders.size(),
        orders.size() - amounts.size(),
        amounts.stream().reduce(ZERO, BigDecimal::add));
  }
}
