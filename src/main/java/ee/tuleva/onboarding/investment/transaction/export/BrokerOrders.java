package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.FT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.investment.transaction.TransactionBatchRefusedException;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

final class BrokerOrders {

  private BrokerOrders() {}

  static boolean isSebFundOrder(TransactionOrder order) {
    return order.getOrderVenue() == SEB && order.getInstrumentType() == FUND;
  }

  static boolean isSebEtfOrder(TransactionOrder order) {
    return order.getOrderVenue() == SEB && order.getInstrumentType() == ETF;
  }

  static boolean isFtOrder(TransactionOrder order) {
    return order.getOrderVenue() == FT && order.getInstrumentType() == ETF;
  }

  static void requireOneVenueFileEach(List<TransactionOrder> orders) {
    List<String> unroutable =
        orders.stream()
            .filter(order -> !isSebFundOrder(order) && !isSebEtfOrder(order) && !isFtOrder(order))
            .map(
                order ->
                    order.getInstrumentIsin()
                        + " "
                        + order.getInstrumentType()
                        + " at "
                        + order.getOrderVenue())
            .toList();
    if (!unroutable.isEmpty()) {
      throw new TransactionBatchRefusedException(
          "No venue file takes these orders, nothing sent: orders=" + unroutable);
    }
  }

  static boolean isSebFundRedemption(TransactionOrder order) {
    return isSebFundOrder(order) && order.getTransactionType() != BUY;
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
