package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.fees.FeeAccrualBuilder.daysInYear;
import static ee.tuleva.onboarding.investment.fees.FeeAccrualBuilder.zeroAccrual;
import static ee.tuleva.onboarding.investment.fees.FeeType.CUSTODY;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.RoundingMode.HALF_UP;

import ee.tuleva.onboarding.investment.TulevaFund;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustodyFeeCalculator implements FeeCalculator {

  private final CustodyFeeInstrumentTypeRepository instrumentTypeRepository;
  private final FundPositionRepository fundPositionRepository;
  private final FeeMonthResolver feeMonthResolver;
  private final VatRateProvider vatRateProvider;

  @Override
  public FeeAccrual calculate(TulevaFund fund, LocalDate calendarDate) {
    LocalDate feeMonth = feeMonthResolver.resolveFeeMonth(calendarDate);

    if (fund != TUV100) {
      return zeroAccrual(fund, CUSTODY, calendarDate, feeMonth);
    }

    LocalDate referenceDate =
        fundPositionRepository
            .findLatestReportingDateByFundAndAsOfDate(TUV100, calendarDate)
            .orElse(null);

    if (referenceDate == null) {
      return zeroAccrual(fund, CUSTODY, calendarDate, feeMonth);
    }

    int daysInYear = daysInYear(calendarDate);
    BigDecimal vatRate = vatRateProvider.getVatRate(feeMonth);

    BigDecimal annualFeeNet = calculateAnnualFeeFromHoldings(feeMonth, referenceDate);
    BigDecimal dailyFeeNet = annualFeeNet.divide(BigDecimal.valueOf(daysInYear), 6, HALF_UP);
    BigDecimal dailyFeeGross = dailyFeeNet.multiply(ONE.add(vatRate)).setScale(6, HALF_UP);

    BigDecimal totalHoldingsValue =
        fundPositionRepository.sumMarketValueByFund(TUV100, referenceDate);

    return FeeAccrual.builder()
        .fund(fund)
        .feeType(CUSTODY)
        .accrualDate(calendarDate)
        .feeMonth(feeMonth)
        .baseValue(totalHoldingsValue)
        .annualRate(ZERO)
        .dailyAmountNet(dailyFeeNet)
        .dailyAmountGross(dailyFeeGross)
        .vatRate(vatRate)
        .daysInYear(daysInYear)
        .referenceDate(referenceDate)
        .build();
  }

  @Override
  public FeeType getFeeType() {
    return CUSTODY;
  }

  private BigDecimal calculateAnnualFeeFromHoldings(LocalDate feeMonth, LocalDate referenceDate) {
    List<CustodyFeeInstrumentType> instrumentTypes =
        instrumentTypeRepository.findAllValidOn(feeMonth);

    if (instrumentTypes.isEmpty()) {
      return ZERO;
    }

    return instrumentTypes.stream()
        .filter(instrumentType -> instrumentType.annualRate() != null)
        .map(
            instrumentType -> {
              BigDecimal holdingValue =
                  fundPositionRepository
                      .findMarketValueByFundAndAccountId(
                          TUV100, instrumentType.isin(), referenceDate)
                      .orElse(ZERO);
              return holdingValue.multiply(instrumentType.annualRate());
            })
        .reduce(ZERO, BigDecimal::add);
  }
}
