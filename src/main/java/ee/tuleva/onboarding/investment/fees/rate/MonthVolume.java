package ee.tuleva.onboarding.investment.fees.rate;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

record MonthVolume(BigDecimal amount, @Nullable LocalDate navDate) {

  static MonthVolume notHeldDuringTheMonth() {
    return new MonthVolume(ZERO, null);
  }
}
