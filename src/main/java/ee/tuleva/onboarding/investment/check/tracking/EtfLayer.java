package ee.tuleva.onboarding.investment.check.tracking;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

record EtfLayer(
    @Nullable BigDecimal measuredSum,
    BigDecimal ocfDrag,
    BigDecimal proxyOcfDrag,
    int coveredDays,
    BigDecimal unbenchmarkedWeight,
    BigDecimal unrestoredProxyWeight) {

  static EtfLayer unmeasured() {
    return new EtfLayer(null, ZERO, ZERO, 0, ZERO, ZERO);
  }
}
