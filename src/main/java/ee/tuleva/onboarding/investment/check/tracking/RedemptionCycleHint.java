package ee.tuleva.onboarding.investment.check.tracking;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

record RedemptionCycleHint(
    boolean executionDate, @Nullable BigDecimal ravaEur, @Nullable BigDecimal pikEur) {

  static RedemptionCycleHint ordinaryDay() {
    return new RedemptionCycleHint(false, null, null);
  }

  static RedemptionCycleHint executionDateWithoutFigures() {
    return new RedemptionCycleHint(true, null, null);
  }

  boolean hasFigures() {
    return ravaEur != null;
  }
}
