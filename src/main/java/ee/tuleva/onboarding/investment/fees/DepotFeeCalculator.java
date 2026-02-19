package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeAccrualBuilder.daysInYear;
import static ee.tuleva.onboarding.investment.fees.FeeAccrualBuilder.zeroAccrual;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.calculation.PositionCalculationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DepotFeeCalculator implements FeeCalculator {

  private static final BigDecimal MIN_ANNUAL_RATE = new BigDecimal("0.00020");

  private final DepotFeeTierRepository tierRepository;
  private final PositionCalculationRepository positionCalculationRepository;
  private final FundAumResolver fundAumResolver;
  private final FeeMonthResolver feeMonthResolver;
  private final VatRateProvider vatRateProvider;
  private final FeeRateRepository feeRateRepository;

  @Override
  public FeeAccrual calculate(TulevaFund fund, LocalDate calendarDate) {
    LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(calendarDate);
    LocalDate referenceDate = fundAumResolver.resolveReferenceDate(fund, calendarDate);

    if (referenceDate == null) {
      return zeroAccrual(fund, DEPOT, calendarDate, feeMonth);
    }

    BigDecimal assetValue = fundAumResolver.resolveBaseValue(fund, referenceDate);
    int daysInYear = daysInYear(calendarDate);

    BigDecimal annualRate = determineDepotRate(fund, feeMonth);
    BigDecimal vatRate = vatRateProvider.getVatRate(feeMonth);

    BigDecimal dailyFeeNet =
        assetValue.multiply(annualRate).divide(BigDecimal.valueOf(daysInYear), 6, HALF_UP);

    BigDecimal dailyFeeGross = dailyFeeNet.multiply(ONE.add(vatRate)).setScale(6, HALF_UP);

    return FeeAccrual.builder()
        .fund(fund)
        .feeType(DEPOT)
        .accrualDate(calendarDate)
        .feeMonth(feeMonth)
        .baseValue(assetValue)
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

  private BigDecimal determineDepotRate(TulevaFund fund, LocalDate feeMonth) {
    return feeRateRepository
        .findValidRate(fund, DEPOT, feeMonth)
        .map(FeeRate::annualRate)
        .orElseGet(() -> determineDepotRateFromTier(feeMonth));
  }

  private BigDecimal determineDepotRateFromTier(LocalDate feeMonth) {
    LocalDate previousMonthEnd = feeMonth.minusDays(1);
    BigDecimal historicalMaxAum = getHistoricalMaxTotalValue(previousMonthEnd);
    BigDecimal tierRate = tierRepository.findRateForAum(historicalMaxAum, feeMonth);
    return tierRate.max(MIN_ANNUAL_RATE);
  }

  private BigDecimal getHistoricalMaxTotalValue(LocalDate upToDate) {
    LocalDate latestDate = positionCalculationRepository.getLatestDateUpTo(upToDate).orElse(null);
    if (latestDate == null) {
      return ZERO;
    }
    return positionCalculationRepository.getTotalMarketValueAllFunds(latestDate).orElse(ZERO);
  }
}
