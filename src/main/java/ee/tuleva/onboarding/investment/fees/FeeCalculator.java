package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;

public interface FeeCalculator {

  FeeAccrual calculate(TulevaFund fund, LocalDate calendarDate);

  FeeType getFeeType();
}
