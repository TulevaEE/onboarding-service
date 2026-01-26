package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.time.LocalDate;

public interface FeeCalculator {

  FeeAccrual calculate(TulevaFund fund, LocalDate calendarDate);

  FeeType getFeeType();
}
