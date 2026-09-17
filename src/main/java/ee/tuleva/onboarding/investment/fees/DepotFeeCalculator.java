package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DepotFeeCalculator implements FeeCalculator {

  private final FeeMonthResolver feeMonthResolver;
  private final DepotRateResolver depotRateResolver;

  @Override
  public FeeAccrual calculate(TulevaFund fund, LocalDate calendarDate, FeeBases bases) {
    LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(calendarDate);

    BigDecimal annualRate = depotRateResolver.resolveAnnualRate(fund, calendarDate);
    BigDecimal assetValue = bases.assetValue();
    int daysInYear = actualDaysInYear(calendarDate);

    BigDecimal dailyFee =
        assetValue.multiply(annualRate).divide(BigDecimal.valueOf(daysInYear), 6, HALF_UP);

    return FeeAccrual.builder()
        .fund(fund)
        .feeType(DEPOT)
        .accrualDate(calendarDate)
        .feeMonth(feeMonth)
        .baseValue(assetValue)
        .annualRate(annualRate)
        .dailyAmountGross(dailyFee)
        .daysInYear(daysInYear)
        .referenceDate(calendarDate)
        .build();
  }

  @Override
  public FeeType getFeeType() {
    return DEPOT;
  }

  private int actualDaysInYear(LocalDate calendarDate) {
    return Year.of(calendarDate.getYear()).length();
  }
}
