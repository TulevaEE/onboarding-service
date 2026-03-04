package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.*;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.portfolio.FundLimit;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
class FreeCashLimitChecker {

  @Nullable FreeCashBreach check(
      TulevaFund fund,
      BigDecimal cashTotal,
      BigDecimal liabilityTotal,
      @Nullable FundLimit fundLimit) {
    if (fundLimit == null
        || fundLimit.getMaxFreeCash() == null
        || fundLimit.getReserveSoft() == null) {
      return null;
    }

    var freeCash = cashTotal.add(liabilityTotal).subtract(fundLimit.getReserveSoft());
    var severity = freeCash.compareTo(fundLimit.getMaxFreeCash()) > 0 ? HARD : OK;
    return new FreeCashBreach(fund, freeCash, fundLimit.getMaxFreeCash(), severity);
  }
}
