package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeAccrualBuilder.daysInYear;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ManagementFeeCalculator implements FeeCalculator {

  private final FeeRateRepository feeRateRepository;
  private final FeeMonthResolver feeMonthResolver;

  @Override
  public FeeAccrual calculate(TulevaFund fund, LocalDate calendarDate, BigDecimal baseValue) {
    LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(calendarDate);
    int daysInYear = daysInYear(calendarDate);

    FeeRate rate =
        feeRateRepository
            .findValidRate(fund, MANAGEMENT, calendarDate)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No management fee rate found: fund=" + fund + ", date=" + calendarDate));

    BigDecimal dailyFee =
        baseValue.multiply(rate.annualRate()).divide(BigDecimal.valueOf(daysInYear), 6, HALF_UP);

    return FeeAccrual.builder()
        .fund(fund)
        .feeType(MANAGEMENT)
        .accrualDate(calendarDate)
        .feeMonth(feeMonth)
        .baseValue(baseValue)
        .annualRate(rate.annualRate())
        .dailyAmountNet(dailyFee)
        .dailyAmountGross(dailyFee)
        .daysInYear(daysInYear)
        .referenceDate(calendarDate)
        .build();
  }

  @Override
  public FeeType getFeeType() {
    return MANAGEMENT;
  }
}
