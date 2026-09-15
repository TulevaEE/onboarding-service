package ee.tuleva.onboarding.nudge;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

final class FundFees {

  static final BigDecimal HIGH_FROM = new BigDecimal("0.003");

  private FundFees() {}

  static boolean isHigh(@Nullable BigDecimal fee) {
    return fee != null && fee.compareTo(HIGH_FROM) >= 0;
  }

  static boolean isLow(@Nullable BigDecimal fee) {
    return fee != null && fee.compareTo(HIGH_FROM) < 0;
  }
}
