package ee.tuleva.onboarding.investment.cashbuffer;

import ee.tuleva.onboarding.investment.portfolio.FundLimit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

record ConfiguredReserve(
    LocalDate effectiveDate, BigDecimal reserveSoft, @Nullable BigDecimal reserveHard) {

  static Optional<ConfiguredReserve> of(FundLimit limit) {
    var reserveSoft = limit.getReserveSoft();
    if (reserveSoft == null) {
      return Optional.empty();
    }
    return Optional.of(
        new ConfiguredReserve(limit.getEffectiveDate(), reserveSoft, limit.getReserveHard()));
  }
}
