package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static java.math.RoundingMode.CEILING;
import static java.math.RoundingMode.FLOOR;
import static java.math.RoundingMode.HALF_UP;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class OrderUnits {

  private static final int ETF_UNIT_SCALE = 0;
  private static final int FUND_UNIT_SCALE = 6;
  private static final BigDecimal HOLDING_LEFT_BELOW_A_CENT_IS_SOLD_WHOLE = new BigDecimal("0.01");

  private OrderUnits() {}

  static BigDecimal of(
      InstrumentType instrumentType,
      TransactionType transactionType,
      BigDecimal orderAmount,
      BigDecimal price) {
    return orderAmount.divide(
        price, scale(instrumentType), roundingMode(instrumentType, transactionType));
  }

  static BigDecimal etfSaleWithin(
      PositionSnapshot holding, BigDecimal heldUnits, BigDecimal units, BigDecimal orderAmount) {
    BigDecimal wholeHeldUnits = heldUnits.setScale(ETF_UNIT_SCALE, FLOOR);
    return sellsWholeHolding(orderAmount, holding) ? wholeHeldUnits : units.min(wholeHeldUnits);
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

  private static boolean sellsWholeHolding(BigDecimal orderAmount, PositionSnapshot holding) {
    return holding
            .marketValue()
            .subtract(orderAmount)
            .compareTo(HOLDING_LEFT_BELOW_A_CENT_IS_SOLD_WHOLE)
        <= 0;
  }
}
