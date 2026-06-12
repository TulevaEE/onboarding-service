package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.investment.transaction.ingest.QuantityAmountMismatchEvent.MismatchKind;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class QuantityAmountValidator {

  Optional<QuantityAmountMismatchEvent> validate(
      TransactionOrder order,
      SebPendingTransactionRow row,
      TransactionMatchingProperties properties) {
    if (expected(order) == null || actual(order, row) == null) {
      return Optional.empty();
    }
    if (withinTolerance(order, row, properties)) {
      return Optional.empty();
    }
    return Optional.of(buildMismatchEvent(order, row, properties));
  }

  boolean withinTolerance(
      TransactionOrder order,
      SebPendingTransactionRow row,
      TransactionMatchingProperties properties) {
    return withinTolerance(order, row, properties, BigDecimal.ONE);
  }

  boolean withinNearMiss(
      TransactionOrder order,
      SebPendingTransactionRow row,
      TransactionMatchingProperties properties) {
    return withinTolerance(order, row, properties, properties.nearMissMultiplier());
  }

  QuantityAmountMismatchEvent buildMismatchEvent(
      TransactionOrder order,
      SebPendingTransactionRow row,
      TransactionMatchingProperties properties) {
    BigDecimal expected = expected(order);
    BigDecimal actual = actual(order, row);
    return new QuantityAmountMismatchEvent(
        row,
        order,
        kind(order),
        expected,
        actual,
        actual.subtract(expected).abs(),
        tolerance(kind(order), properties),
        properties.nearMissMultiplier(),
        null);
  }

  private boolean withinTolerance(
      TransactionOrder order,
      SebPendingTransactionRow row,
      TransactionMatchingProperties properties,
      BigDecimal multiplier) {
    MismatchKind kind = kind(order);
    BigDecimal tolerance = tolerance(kind, properties).multiply(multiplier);
    if (kind == MismatchKind.FUND_BUY_AMOUNT) {
      return amountWithinRelativeTolerance(expected(order), actual(order, row), tolerance);
    }
    return quantityWithinTolerance(expected(order), actual(order, row), tolerance);
  }

  private static BigDecimal tolerance(MismatchKind kind, TransactionMatchingProperties properties) {
    return switch (kind) {
      case ETF_QUANTITY -> properties.etfQuantityTolerance();
      case FUND_BUY_AMOUNT -> properties.fundBuyAmountTolerance();
      case FUND_SELL_QUANTITY -> properties.fundSellQuantityTolerance();
    };
  }

  private static MismatchKind kind(TransactionOrder order) {
    if (order.getInstrumentType() == InstrumentType.ETF) {
      return MismatchKind.ETF_QUANTITY;
    }
    if (order.getTransactionType() == TransactionType.BUY) {
      return MismatchKind.FUND_BUY_AMOUNT;
    }
    return MismatchKind.FUND_SELL_QUANTITY;
  }

  private static BigDecimal expected(TransactionOrder order) {
    return kind(order) == MismatchKind.FUND_BUY_AMOUNT
        ? order.getOrderAmount()
        : order.getOrderQuantity();
  }

  private static BigDecimal actual(TransactionOrder order, SebPendingTransactionRow row) {
    return kind(order) == MismatchKind.FUND_BUY_AMOUNT ? row.total() : row.quantity();
  }

  private static boolean quantityWithinTolerance(
      BigDecimal orderQuantity, BigDecimal execQuantity, BigDecimal tolerance) {
    if (orderQuantity == null || execQuantity == null) {
      return false;
    }
    BigDecimal diff = execQuantity.subtract(orderQuantity).abs();
    return diff.compareTo(tolerance) < 0;
  }

  private static boolean amountWithinRelativeTolerance(
      BigDecimal orderAmount, BigDecimal execAmount, BigDecimal tolerance) {
    if (orderAmount == null || execAmount == null) {
      return false;
    }
    if (orderAmount.signum() == 0) {
      return false;
    }
    BigDecimal relativeDelta =
        execAmount.subtract(orderAmount).abs().divide(orderAmount.abs(), MathContext.DECIMAL64);
    return relativeDelta.compareTo(tolerance) < 0;
  }
}
