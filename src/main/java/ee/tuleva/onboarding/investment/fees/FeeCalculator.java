package ee.tuleva.onboarding.investment.fees;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface FeeCalculator {

  FeeAccrual calculate(TulevaFund fund, LocalDate calendarDate, BigDecimal baseValue);

  FeeType getFeeType();
}
