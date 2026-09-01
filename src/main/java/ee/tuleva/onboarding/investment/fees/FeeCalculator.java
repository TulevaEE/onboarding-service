package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;

public interface FeeCalculator {

  FeeAccrual calculate(TulevaFund fund, LocalDate calendarDate, FeeBases bases);

  FeeType getFeeType();
}
