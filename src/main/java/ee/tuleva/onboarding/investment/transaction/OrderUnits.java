package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.FLOOR;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jspecify.annotations.Nullable;

final class OrderUnits {

  private static final int ETF_UNIT_SCALE = 0;
  private static final int FUND_UNIT_SCALE = 6;

  private OrderUnits() {}

  static BigDecimal of(
      InstrumentType instrumentType,
      TransactionType transactionType,
      BigDecimal orderAmount,
      BigDecimal price) {
    return orderAmount.divide(
        price, scale(instrumentType), roundingMode(instrumentType, transactionType));
  }

  static BigDecimal sellableAtMost(InstrumentType instrumentType, BigDecimal units) {
    return units.setScale(scale(instrumentType), FLOOR);
  }

  static boolean fitsAnOrder(InstrumentType instrumentType, @Nullable BigDecimal quantity) {
    if (quantity == null || quantity.signum() <= 0) {
      return false;
    }
    return instrumentType != ETF || quantity.remainder(BigDecimal.ONE).signum() == 0;
  }

  private static int scale(InstrumentType instrumentType) {
    return instrumentType == ETF ? ETF_UNIT_SCALE : FUND_UNIT_SCALE;
  }

  private static RoundingMode roundingMode(
      InstrumentType instrumentType, TransactionType transactionType) {
    if (instrumentType != ETF) {
      return HALF_UP;
    }
    return transactionType == BUY ? FLOOR : CEILING;
  }
}
