package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.fees.FeeAccrualBuilder.DAYS_IN_YEAR;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DepotFeeCalculator implements FeeCalculator {

  private final DepotFeeTierRepository tierRepository;
  private final FundPositionRepository fundPositionRepository;
  private final FeeMonthResolver feeMonthResolver;
  private final FeeRateRepository feeRateRepository;

  @Override
  public FeeAccrual calculate(TulevaFund fund, LocalDate calendarDate, BigDecimal baseValue) {
    LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(calendarDate);

    BigDecimal annualRate = determineDepotRate(fund, calendarDate, feeMonth);

    BigDecimal dailyFee =
        baseValue.multiply(annualRate).divide(BigDecimal.valueOf(DAYS_IN_YEAR), 6, HALF_UP);

    return FeeAccrual.builder()
        .fund(fund)
        .feeType(DEPOT)
        .accrualDate(calendarDate)
        .feeMonth(feeMonth)
        .baseValue(baseValue)
        .annualRate(annualRate)
        .dailyAmountNet(dailyFee)
        .dailyAmountGross(dailyFee)
        .daysInYear(DAYS_IN_YEAR)
        .referenceDate(calendarDate)
        .build();
  }

  @Override
  public FeeType getFeeType() {
    return DEPOT;
  }

  /**
   * A row valid on the accrual date decides the rate: a TIER row reads the AUM tier for the fee
   * month, a FIXED row carries the rate itself. No row means no depot fee — never the tier, so that
   * a lapsed or deleted row cannot silently start charging one.
   */
  private BigDecimal determineDepotRate(
      TulevaFund fund, LocalDate calendarDate, LocalDate feeMonth) {
    Optional<FeeRate> rate = feeRateRepository.findValidRate(fund, DEPOT, calendarDate);
    if (rate.isEmpty()) {
      log.warn("No depot fee rate configured, accruing zero: fund={}, date={}", fund, calendarDate);
      return ZERO;
    }
    return rate.get().isTierBased()
        ? determineDepotRateFromTier(feeMonth)
        : rate.get().annualRate();
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
