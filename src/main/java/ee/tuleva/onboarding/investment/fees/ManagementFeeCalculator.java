package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeAccrualBuilder.daysInYear;
import static ee.tuleva.onboarding.investment.fees.FeeAccrualBuilder.zeroAccrual;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ManagementFeeCalculator implements FeeCalculator {

  private final FeeRateRepository feeRateRepository;
  private final FundAumResolver fundAumResolver;
  private final FeeAccrualRepository feeAccrualRepository;
  private final FeeMonthResolver feeMonthResolver;

  @Override
  public FeeAccrual calculate(TulevaFund fund, LocalDate calendarDate) {
    LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(calendarDate);
    LocalDate referenceDate = fundAumResolver.resolveReferenceDate(fund, calendarDate);

    if (referenceDate == null) {
      return zeroAccrual(fund, MANAGEMENT, calendarDate, feeMonth);
    }

    BigDecimal baseValue = resolveBaseValue(fund, referenceDate, feeMonth, calendarDate);
    int daysInYear = daysInYear(calendarDate);

    FeeRate rate =
        feeRateRepository
            .findValidRate(fund, MANAGEMENT, referenceDate)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No management fee rate found: fund=" + fund + ", date=" + referenceDate));

    BigDecimal dailyFee =
        baseValue.multiply(rate.annualRate()).divide(BigDecimal.valueOf(daysInYear), 2, HALF_UP);

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
        .referenceDate(referenceDate)
        .build();
  }

  private BigDecimal resolveBaseValue(
      TulevaFund fund, LocalDate referenceDate, LocalDate feeMonth, LocalDate calendarDate) {
    BigDecimal baseValue = fundAumResolver.resolveBaseValue(fund, referenceDate);
    if (!fund.hasNavCalculation()) {
      baseValue =
          baseValue.add(
              feeAccrualRepository.getAccruedFeesForMonth(
                  fund, feeMonth, List.of(MANAGEMENT, DEPOT), calendarDate));
    }
    return baseValue;
  }

  @Override
  public FeeType getFeeType() {
    return MANAGEMENT;
  }
}
