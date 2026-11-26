package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.FT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

final class BrokerOrders {

  private BrokerOrders() {}

  static boolean isSebFundOrder(TransactionOrder order) {
    return order.getInstrumentType() == FUND;
  }

  static boolean isSebEtfOrder(TransactionOrder order) {
    return order.getOrderVenue() == SEB && order.getInstrumentType() == ETF;
  }

  static boolean isFtOrder(TransactionOrder order) {
    return order.getOrderVenue() == FT;
  }

  static boolean isUuidWorkbookOrder(TransactionOrder order) {
    return isFtOrder(order) || isSebEtfOrder(order);
  }

  static BigDecimal ftApproximateTotal(List<TransactionOrder> orders) {
    return orders.stream()
        .filter(BrokerOrders::isFtOrder)
        .map(TransactionOrder::getOrderAmount)
        .filter(Objects::nonNull)
        .reduce(ZERO, BigDecimal::add);
  }
}
