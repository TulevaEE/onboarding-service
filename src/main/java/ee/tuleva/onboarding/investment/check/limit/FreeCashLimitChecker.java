package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.HARD;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static ee.tuleva.onboarding.investment.check.limit.LimitSeverity.freeCashAboveMaximum;

import ee.tuleva.onboarding.investment.check.limit.LimitSeverity.FreeCash;
import ee.tuleva.onboarding.investment.portfolio.FundLimit;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class FreeCashLimitChecker {

  @Nullable FreeCashBreach check(
      TulevaFund fund,
      BigDecimal cashTotal,
      BigDecimal liabilityTotal,
      BigDecimal pendingCashImpact,
      @Nullable FundLimit fundLimit) {
    if (fundLimit == null
        || fundLimit.getMaxFreeCash() == null
        || fundLimit.getReserveSoft() == null) {
      return null;
    }

    var freeCash =
        cashTotal
            .add(liabilityTotal)
            .subtract(pendingCashImpact)
            .subtract(fundLimit.getReserveSoft());
    var severity =
        freeCashAboveMaximum(new FreeCash(freeCash, fundLimit.getMaxFreeCash())) ? HARD : OK;
    return new FreeCashBreach(
        fund,
        freeCash,
        fundLimit.getMaxFreeCash(),
        severity,
        cashTotal,
        liabilityTotal,
        pendingCashImpact,
        fundLimit.getReserveSoft());
  }
}
