package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepotFeeCalculatorTest {

  @Mock private DepotFeeTierRepository tierRepository;
  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private FeeMonthResolver feeMonthResolver;
  @Mock private VatRateProvider vatRateProvider;
  @Mock private FeeRateRepository feeRateRepository;

  @InjectMocks private DepotFeeCalculator calculator;

  @Test
  void calculate_returnsCorrectDailyFeeWithVat() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal baseValue = new BigDecimal("500000000");
    BigDecimal fundRate = new BigDecimal("0.00035");
    BigDecimal vatRate = new BigDecimal("0.24");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TKF100, FeeType.DEPOT, feeMonth))
        .thenReturn(Optional.of(new FeeRate(1L, TKF100, FeeType.DEPOT, fundRate, feeMonth, null)));
    when(vatRateProvider.getVatRate(feeMonth)).thenReturn(vatRate);

    FeeAccrual result = calculator.calculate(TKF100, date, baseValue);

    assertThat(result.fund()).isEqualTo(TKF100);
    assertThat(result.feeType()).isEqualTo(FeeType.DEPOT);
    assertThat(result.accrualDate()).isEqualTo(date);
    assertThat(result.feeMonth()).isEqualTo(feeMonth);
    assertThat(result.baseValue()).isEqualTo(baseValue);
    assertThat(result.annualRate()).isEqualTo(fundRate);
    assertThat(result.vatRate()).isEqualTo(vatRate);
    assertThat(result.referenceDate()).isEqualTo(date);
    assertThat(result.daysInYear()).isEqualTo(365);

    BigDecimal expectedDailyNet =
        baseValue.multiply(fundRate).divide(BigDecimal.valueOf(365), 6, RoundingMode.HALF_UP);
    BigDecimal expectedDailyGross =
        expectedDailyNet.multiply(ONE.add(vatRate)).setScale(6, RoundingMode.HALF_UP);

    assertThat(result.dailyAmountNet()).isEqualByComparingTo(expectedDailyNet);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(expectedDailyGross);
  }

  @Test
  void calculate_handlesLeapYear() {
    LocalDate date = LocalDate.of(2024, 2, 29);
    LocalDate feeMonth = LocalDate.of(2024, 2, 1);
    BigDecimal baseValue = new BigDecimal("100000000");
    BigDecimal fundRate = new BigDecimal("0.00035");
    BigDecimal vatRate = new BigDecimal("0.22");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TKF100, FeeType.DEPOT, feeMonth))
        .thenReturn(Optional.of(new FeeRate(1L, TKF100, FeeType.DEPOT, fundRate, feeMonth, null)));
    when(vatRateProvider.getVatRate(feeMonth)).thenReturn(vatRate);

    FeeAccrual result = calculator.calculate(TKF100, date, baseValue);

    assertThat(result.daysInYear()).isEqualTo(366);

    BigDecimal expectedDailyNet =
        baseValue.multiply(fundRate).divide(BigDecimal.valueOf(366), 6, RoundingMode.HALF_UP);
    assertThat(result.dailyAmountNet()).isEqualByComparingTo(expectedDailyNet);
  }

  @Test
  void calculate_returnsZeroFeeWhenFundRateIsZero() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    BigDecimal baseValue = new BigDecimal("500000000");
    BigDecimal vatRate = new BigDecimal("0.24");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TKF100, FeeType.DEPOT, feeMonth))
        .thenReturn(Optional.of(new FeeRate(1L, TKF100, FeeType.DEPOT, ZERO, feeMonth, null)));
    when(vatRateProvider.getVatRate(feeMonth)).thenReturn(vatRate);

    FeeAccrual result = calculator.calculate(TKF100, date, baseValue);

    assertThat(result.annualRate()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountNet()).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_fallsBackToTierWhenNoFundRate() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    LocalDate previousMonthEnd = LocalDate.of(2025, 6, 30);
    BigDecimal baseValue = new BigDecimal("500000000");
    BigDecimal totalAum = new BigDecimal("1400000000");
    BigDecimal tierRate = new BigDecimal("0.00025");
    BigDecimal vatRate = new BigDecimal("0.24");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TUK75, FeeType.DEPOT, feeMonth))
        .thenReturn(Optional.empty());
    when(fundPositionRepository.findLatestSecurityNavDateUpTo(LocalDate.of(2025, 6, 30)))
        .thenReturn(Optional.of(previousMonthEnd));
    when(fundPositionRepository.sumSecurityMarketValueAllFunds(previousMonthEnd))
        .thenReturn(totalAum);
    when(tierRepository.findRateForAum(totalAum, feeMonth)).thenReturn(tierRate);
    when(vatRateProvider.getVatRate(feeMonth)).thenReturn(vatRate);

    FeeAccrual result = calculator.calculate(TUK75, date, baseValue);

    assertThat(result.annualRate()).isEqualByComparingTo(tierRate);
  }

  @Test
  void getFeeType_returnsDepot() {
    assertThat(calculator.getFeeType()).isEqualTo(FeeType.DEPOT);
  }
}
