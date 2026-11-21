package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import org.jspecify.annotations.NullMarked;

@NullMarked
record SellableUnits(BigDecimal held, BigDecimal pendingSells) {

  BigDecimal allowed() {
    return ZERO.max(held.subtract(pendingSells));
  }
}
