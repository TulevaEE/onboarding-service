package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeAccrualBuilder.daysInYear;
import static ee.tuleva.onboarding.investment.fees.FeeAccrualBuilder.zeroAccrual;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ManagementFeeCalculator implements FeeCalculator {

  private final FeeRateRepository feeRateRepository;
  private final AumRepository aumRepository;
  private final FeeMonthResolver feeMonthResolver;

  @Override
  public FeeAccrual calculate(TulevaFund fund, LocalDate calendarDate) {
    LocalDate referenceDate = aumRepository.getAumReferenceDate(fund, calendarDate);
    LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(calendarDate);

    if (referenceDate == null) {
      return zeroAccrual(fund, MANAGEMENT, calendarDate, feeMonth);
    }

    BigDecimal nav = aumRepository.getAum(fund, referenceDate).orElse(ZERO);
    int daysInYear = daysInYear(calendarDate);

    FeeRate rate =
        feeRateRepository
            .findValidRate(fund, MANAGEMENT, referenceDate)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No management fee rate found: fund=" + fund + ", date=" + referenceDate));

    BigDecimal dailyFee =
        nav.multiply(rate.annualRate()).divide(BigDecimal.valueOf(daysInYear), 6, HALF_UP);

    return FeeAccrual.builder()
        .fund(fund)
        .feeType(MANAGEMENT)
        .accrualDate(calendarDate)
        .feeMonth(feeMonth)
        .baseValue(nav)
        .annualRate(rate.annualRate())
        .dailyAmountNet(dailyFee)
        .dailyAmountGross(dailyFee)
        .daysInYear(daysInYear)
        .referenceDate(referenceDate)
        .build();
  }

  @Override
  public FeeType getFeeType() {
    return MANAGEMENT;
  }
}
