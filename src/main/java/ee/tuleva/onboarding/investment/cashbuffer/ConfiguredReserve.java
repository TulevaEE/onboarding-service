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

  boolean hasTheSameSoftLimitAs(ConfiguredReserve other) {
    return reserveSoft.compareTo(other.reserveSoft) == 0;
  }

  boolean hasTheSameHardLimitAs(ConfiguredReserve other) {
    return reserveHard != null
        && other.reserveHard != null
        && reserveHard.compareTo(other.reserveHard) == 0;
  }
}
