package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeAccrualBuilder.DAYS_IN_YEAR;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DepotFeeCalculator implements FeeCalculator {

  private final DepotFeeTierRepository tierRepository;
  private final FundPositionRepository fundPositionRepository;
  private final FeeMonthResolver feeMonthResolver;
  private final VatRateProvider vatRateProvider;
  private final FeeRateRepository feeRateRepository;

  @Override
  public FeeAccrual calculate(TulevaFund fund, LocalDate calendarDate, BigDecimal baseValue) {
    LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(calendarDate);

    BigDecimal annualRate = determineDepotRate(fund, feeMonth);
    BigDecimal vatRate = vatRateProvider.getVatRate(feeMonth);

    BigDecimal dailyFeeNet =
        baseValue.multiply(annualRate).divide(BigDecimal.valueOf(DAYS_IN_YEAR), 6, HALF_UP);

    BigDecimal dailyFeeGross = dailyFeeNet.multiply(ONE.add(vatRate)).setScale(6, HALF_UP);

    return FeeAccrual.builder()
        .fund(fund)
        .feeType(DEPOT)
        .accrualDate(calendarDate)
        .feeMonth(feeMonth)
        .baseValue(baseValue)
        .annualRate(annualRate)
        .dailyAmountNet(dailyFeeNet)
        .dailyAmountGross(dailyFeeGross)
        .vatRate(vatRate)
        .daysInYear(DAYS_IN_YEAR)
        .referenceDate(calendarDate)
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
    return tierRepository.findRateForAum(historicalMaxAum, feeMonth);
  }

  private BigDecimal getHistoricalMaxTotalValue(LocalDate upToDate) {
    LocalDate latestDate =
        fundPositionRepository.findLatestSecurityNavDateUpTo(upToDate).orElse(null);
    if (latestDate == null) {
      return ZERO;
    }
    return fundPositionRepository.sumSecurityMarketValueAllFunds(latestDate);
  }
}
