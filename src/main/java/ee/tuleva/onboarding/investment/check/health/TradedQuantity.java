package ee.tuleva.onboarding.investment.check.health;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;

record TradedQuantity(BigDecimal bought, BigDecimal sold) {

  static final TradedQuantity NONE = new TradedQuantity(ZERO, ZERO);

  BigDecimal netChange() {
    return bought.subtract(sold);
  }
}
