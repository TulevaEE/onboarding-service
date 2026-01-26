package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeAccrualBuilder.daysInYear;
import static ee.tuleva.onboarding.investment.fees.FeeAccrualBuilder.zeroAccrual;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DepotFeeCalculator implements FeeCalculator {

  private static final BigDecimal MIN_ANNUAL_RATE = new BigDecimal("0.00020");

  private final DepotFeeTierRepository tierRepository;
  private final AumRepository aumRepository;
  private final FeeMonthResolver feeMonthResolver;
  private final VatRateProvider vatRateProvider;

  @Override
  public FeeAccrual calculate(TulevaFund fund, LocalDate calendarDate) {
    LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(calendarDate);
    LocalDate referenceDate = aumRepository.getAumReferenceDate(fund, calendarDate);

    if (referenceDate == null) {
      return zeroAccrual(fund, DEPOT, calendarDate, feeMonth);
    }

    BigDecimal fundAum = aumRepository.getAum(fund, referenceDate).orElse(ZERO);
    int daysInYear = daysInYear(calendarDate);

    LocalDate previousMonthEnd = feeMonth.minusDays(1);
    BigDecimal historicalMaxAum = aumRepository.getHistoricalMaxTotalAum(previousMonthEnd);

    BigDecimal annualRate = determineDepotRate(historicalMaxAum, feeMonth);
    BigDecimal vatRate = vatRateProvider.getVatRate(feeMonth);

    BigDecimal dailyFeeNet =
        fundAum.multiply(annualRate).divide(BigDecimal.valueOf(daysInYear), 6, HALF_UP);

    BigDecimal dailyFeeGross = dailyFeeNet.multiply(ONE.add(vatRate)).setScale(6, HALF_UP);

    return FeeAccrual.builder()
        .fund(fund)
        .feeType(DEPOT)
        .accrualDate(calendarDate)
        .feeMonth(feeMonth)
        .baseValue(fundAum)
        .annualRate(annualRate)
        .dailyAmountNet(dailyFeeNet)
        .dailyAmountGross(dailyFeeGross)
        .vatRate(vatRate)
        .daysInYear(daysInYear)
        .referenceDate(referenceDate)
        .build();
  }

  @Override
  public FeeType getFeeType() {
    return DEPOT;
  }

  private BigDecimal determineDepotRate(BigDecimal totalAum, LocalDate feeMonth) {
    BigDecimal tierRate = tierRepository.findRateForAum(totalAum, feeMonth);
    return tierRate.max(MIN_ANNUAL_RATE);
  }
}
