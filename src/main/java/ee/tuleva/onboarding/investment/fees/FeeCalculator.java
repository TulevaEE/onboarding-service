package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;

public interface FeeCalculator {

  /** Each implementation takes from {@link FeeBases} the base its own contract names. */
  FeeAccrual calculate(TulevaFund fund, LocalDate calendarDate, FeeBases bases);

  FeeType getFeeType();
}
