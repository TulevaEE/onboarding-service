package ee.tuleva.onboarding.investment.fees.rate;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.time.YearMonth;

class MonthNavNotYetPublishedException extends IllegalStateException {

  MonthNavNotYetPublishedException(TulevaFund fund, YearMonth month, LocalDate lastNavDate) {
    super(
        "The month's last NAV is not yet published: fund="
            + fund.getCode()
            + ", month="
            + month
            + ", navDate="
            + lastNavDate);
  }
}
