package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record SellableUnits(@Nullable BigDecimal held, BigDecimal pendingSells) {

  BigDecimal allowed() {
    return held == null ? ZERO : ZERO.max(held.subtract(pendingSells));
  }
}
