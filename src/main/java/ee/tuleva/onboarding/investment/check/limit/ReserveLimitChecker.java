package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.*;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.portfolio.FundLimit;
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
    if (cashBalance.compareTo(fundLimit.getReserveHard()) < 0) {
      return HARD;
    }
    if (cashBalance.compareTo(fundLimit.getReserveSoft()) < 0) {
      return SOFT;
    }
    return OK;
  }
}
