package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.TulevaFund.TUK00;
import static ee.tuleva.onboarding.investment.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.fees.CustodyInstrumentType.ETF;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustodyFeeCalculatorTest {

  @Mock private CustodyFeeInstrumentTypeRepository instrumentTypeRepository;
  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private FeeMonthResolver feeMonthResolver;
  @Mock private VatRateProvider vatRateProvider;

  @InjectMocks private CustodyFeeCalculator calculator;

  @Test
  void calculate_returnsZeroAccrualForNonTUV100Fund() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);
    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);

    FeeAccrual result = calculator.calculate(TUK75, date);

    assertThat(result.fund()).isEqualTo(TUK75);
    assertThat(result.feeType()).isEqualTo(FeeType.CUSTODY);
    assertThat(result.dailyAmountNet()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_returnsZeroAccrualForTUK00Fund() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);
    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);

    FeeAccrual result = calculator.calculate(TUK00, date);

    assertThat(result.fund()).isEqualTo(TUK00);
    assertThat(result.feeType()).isEqualTo(FeeType.CUSTODY);
    assertThat(result.dailyAmountNet()).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_returnsZeroAccrualWhenNoPositionData() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(fundPositionRepository.findLatestReportingDateByFundAndAsOfDate(TUV100, date))
        .thenReturn(Optional.empty());

    FeeAccrual result = calculator.calculate(TUV100, date);

    assertThat(result.dailyAmountNet()).isEqualByComparingTo(ZERO);
    assertThat(result.referenceDate()).isNull();
  }

  @Test
  void calculate_returnsFeeAccrualForTUV100() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    LocalDate referenceDate = LocalDate.of(2025, 1, 14);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);
    BigDecimal vatRate = new BigDecimal("0.22");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(fundPositionRepository.findLatestReportingDateByFundAndAsOfDate(TUV100, date))
        .thenReturn(Optional.of(referenceDate));
    when(vatRateProvider.getVatRate(feeMonth)).thenReturn(vatRate);
    when(instrumentTypeRepository.findAllValidOn(feeMonth)).thenReturn(List.of());
    when(fundPositionRepository.sumMarketValueByFund(TUV100, referenceDate)).thenReturn(ZERO);

    FeeAccrual result = calculator.calculate(TUV100, date);

    assertThat(result).isNotNull();
    assertThat(result.fund()).isEqualTo(TUV100);
    assertThat(result.feeType()).isEqualTo(FeeType.CUSTODY);
    assertThat(result.accrualDate()).isEqualTo(date);
    assertThat(result.feeMonth()).isEqualTo(feeMonth);
    assertThat(result.vatRate()).isEqualTo(vatRate);
    assertThat(result.referenceDate()).isEqualTo(referenceDate);
  }

  @Test
  void calculate_calculatesCorrectFeeWithHoldings() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    LocalDate referenceDate = LocalDate.of(2025, 1, 14);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);
    BigDecimal vatRate = new BigDecimal("0.22");
    BigDecimal holdingValue = new BigDecimal("10000000");
    BigDecimal annualRate = new BigDecimal("0.0002");
    int daysInYear = 365;

    CustodyFeeInstrumentType etf =
        new CustodyFeeInstrumentType(1L, "IE00TEST1234", ETF, annualRate, feeMonth, null);

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(fundPositionRepository.findLatestReportingDateByFundAndAsOfDate(TUV100, date))
        .thenReturn(Optional.of(referenceDate));
    when(vatRateProvider.getVatRate(feeMonth)).thenReturn(vatRate);
    when(instrumentTypeRepository.findAllValidOn(feeMonth)).thenReturn(List.of(etf));
    when(fundPositionRepository.findMarketValueByFundAndAccountId(
            eq(TUV100), eq("IE00TEST1234"), eq(referenceDate)))
        .thenReturn(Optional.of(holdingValue));
    when(fundPositionRepository.sumMarketValueByFund(TUV100, referenceDate))
        .thenReturn(holdingValue);

    FeeAccrual result = calculator.calculate(TUV100, date);

    assertThat(result).isNotNull();
    assertThat(result.dailyAmountNet()).isPositive();
    assertThat(result.dailyAmountGross()).isGreaterThan(result.dailyAmountNet());
    assertThat(result.referenceDate()).isEqualTo(referenceDate);

    BigDecimal expectedAnnualFee = holdingValue.multiply(annualRate);
    BigDecimal expectedDailyNet =
        expectedAnnualFee.divide(BigDecimal.valueOf(daysInYear), 6, RoundingMode.HALF_UP);

    assertThat(result.dailyAmountNet()).isEqualByComparingTo(expectedDailyNet);
  }

  @Test
  void calculate_returnsZeroFeeWhenNoInstrumentTypes() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    LocalDate referenceDate = LocalDate.of(2025, 1, 14);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(fundPositionRepository.findLatestReportingDateByFundAndAsOfDate(TUV100, date))
        .thenReturn(Optional.of(referenceDate));
    when(vatRateProvider.getVatRate(feeMonth)).thenReturn(new BigDecimal("0.22"));
    when(instrumentTypeRepository.findAllValidOn(feeMonth)).thenReturn(List.of());
    when(fundPositionRepository.sumMarketValueByFund(TUV100, referenceDate)).thenReturn(ZERO);

    FeeAccrual result = calculator.calculate(TUV100, date);

    assertThat(result.dailyAmountNet()).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_appliesVatCorrectly() {
    LocalDate date = LocalDate.of(2025, 6, 15);
    LocalDate referenceDate = LocalDate.of(2025, 6, 14);
    LocalDate feeMonth = LocalDate.of(2025, 6, 1);
    BigDecimal vatRate = new BigDecimal("0.24");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(fundPositionRepository.findLatestReportingDateByFundAndAsOfDate(TUV100, date))
        .thenReturn(Optional.of(referenceDate));
    when(vatRateProvider.getVatRate(feeMonth)).thenReturn(vatRate);
    when(instrumentTypeRepository.findAllValidOn(feeMonth)).thenReturn(List.of());
    when(fundPositionRepository.sumMarketValueByFund(TUV100, referenceDate)).thenReturn(ZERO);

    FeeAccrual result = calculator.calculate(TUV100, date);

    assertThat(result.vatRate()).isEqualByComparingTo(vatRate);
  }

  @Test
  void getFeeType_returnsCustody() {
    assertThat(calculator.getFeeType()).isEqualTo(FeeType.CUSTODY);
  }
}
