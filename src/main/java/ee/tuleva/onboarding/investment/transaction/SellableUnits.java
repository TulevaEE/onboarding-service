package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record SellableUnits(@Nullable BigDecimal held, BigDecimal pendingSells) {

  BigDecimal allowed() {
    return ZERO.max(Objects.requireNonNull(held).subtract(pendingSells));
  }

  SellableUnits togetherWith(SellableUnits anotherRow) {
    BigDecimal other = anotherRow.held();
    if (held == null || other == null) {
      return new SellableUnits(held == null ? other : held, pendingSells);
    }
    return new SellableUnits(held.add(other), pendingSells);
  }
}
