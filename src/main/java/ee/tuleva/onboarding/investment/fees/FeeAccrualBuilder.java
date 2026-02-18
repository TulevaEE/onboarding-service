package ee.tuleva.onboarding.investment.fees;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.time.Year;

public class FeeAccrualBuilder {

  public static int daysInYear(LocalDate date) {
    return Year.of(date.getYear()).length();
  }

  public static FeeAccrual zeroAccrual(
      TulevaFund fund, FeeType feeType, LocalDate calendarDate, LocalDate feeMonth) {
    return FeeAccrual.builder()
        .fund(fund)
        .feeType(feeType)
        .accrualDate(calendarDate)
        .feeMonth(feeMonth)
        .baseValue(ZERO)
        .annualRate(ZERO)
        .dailyAmountNet(ZERO)
        .dailyAmountGross(ZERO)
        .daysInYear(daysInYear(calendarDate))
        .build();
  }
}
