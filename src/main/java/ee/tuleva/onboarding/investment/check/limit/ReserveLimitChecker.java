package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.LimitSeverity.SISEKORD_4_P_9_5;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.investment.check.limit.LimitSeverity.Reading;
import ee.tuleva.onboarding.investment.portfolio.FundLimit;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class ReserveLimitChecker {

  @Nullable ReserveBreach check(
      TulevaFund fund, BigDecimal cashBalance, @Nullable FundLimit fundLimit) {
    if (fundLimit == null
        || fundLimit.getReserveSoft() == null
        || fundLimit.getReserveHard() == null) {
      return null;
    }

    var severity = determineSeverity(cashBalance, fundLimit);
    return new ReserveBreach(
        fund, cashBalance, fundLimit.getReserveSoft(), fundLimit.getReserveHard(), severity);
  }

  private BreachSeverity determineSeverity(BigDecimal cashBalance, FundLimit fundLimit) {
    return SISEKORD_4_P_9_5.decide(
        new Reading(
            cashBalance,
            requireNonNull(fundLimit.getReserveSoft()),
            requireNonNull(fundLimit.getReserveHard())));
  }
}
