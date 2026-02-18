package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.investment.calculation.PositionCalculationRepository;
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
  @Mock private PositionCalculationRepository positionCalculationRepository;
  @Mock private FeeMonthResolver feeMonthResolver;
  @Mock private VatRateProvider vatRateProvider;

  @InjectMocks private DepotFeeCalculator calculator;

  @Test
  void calculate_returnsCorrectDailyFeeWithVat() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    LocalDate previousMonthEnd = LocalDate.of(2025, 6, 30);
    BigDecimal assetValue = new BigDecimal("500000000");
    BigDecimal totalAum = new BigDecimal("1400000000");
    BigDecimal tierRate = new BigDecimal("0.00025");
    BigDecimal vatRate = new BigDecimal("0.24");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(positionCalculationRepository.getLatestDateUpTo(TUK75, date))
        .thenReturn(Optional.of(date));
    when(positionCalculationRepository.getTotalMarketValue(TUK75, date))
        .thenReturn(Optional.of(assetValue));
    when(positionCalculationRepository.getLatestDateUpTo(previousMonthEnd))
        .thenReturn(Optional.of(previousMonthEnd));
    when(positionCalculationRepository.getTotalMarketValueAllFunds(previousMonthEnd))
        .thenReturn(Optional.of(totalAum));
    when(tierRepository.findRateForAum(totalAum, feeMonth)).thenReturn(tierRate);
    when(vatRateProvider.getVatRate(feeMonth)).thenReturn(vatRate);

    FeeAccrual result = calculator.calculate(TUK75, date);

    assertThat(result.fund()).isEqualTo(TUK75);
    assertThat(result.feeType()).isEqualTo(FeeType.DEPOT);
    assertThat(result.accrualDate()).isEqualTo(date);
    assertThat(result.feeMonth()).isEqualTo(feeMonth);
    assertThat(result.baseValue()).isEqualTo(assetValue);
    assertThat(result.annualRate()).isEqualTo(tierRate);
    assertThat(result.vatRate()).isEqualTo(vatRate);
    assertThat(result.daysInYear()).isEqualTo(365);

    BigDecimal expectedDailyNet =
        assetValue.multiply(tierRate).divide(BigDecimal.valueOf(365), 6, RoundingMode.HALF_UP);
    BigDecimal expectedDailyGross =
        expectedDailyNet.multiply(BigDecimal.ONE.add(vatRate)).setScale(6, RoundingMode.HALF_UP);

    assertThat(result.dailyAmountNet()).isEqualByComparingTo(expectedDailyNet);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(expectedDailyGross);
  }

  @Test
  void calculate_usesMinimumRate_whenTierRateIsBelowMinimum() {
    LocalDate date = LocalDate.of(2025, 7, 15);
    LocalDate feeMonth = LocalDate.of(2025, 7, 1);
    LocalDate previousMonthEnd = LocalDate.of(2025, 6, 30);
    BigDecimal assetValue = new BigDecimal("500000000");
    BigDecimal totalAum = new BigDecimal("3000000000");
    BigDecimal tierRate = new BigDecimal("0.00015");
    BigDecimal vatRate = new BigDecimal("0.24");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(positionCalculationRepository.getLatestDateUpTo(TUK75, date))
        .thenReturn(Optional.of(date));
    when(positionCalculationRepository.getTotalMarketValue(TUK75, date))
        .thenReturn(Optional.of(assetValue));
    when(positionCalculationRepository.getLatestDateUpTo(previousMonthEnd))
        .thenReturn(Optional.of(previousMonthEnd));
    when(positionCalculationRepository.getTotalMarketValueAllFunds(previousMonthEnd))
        .thenReturn(Optional.of(totalAum));
    when(tierRepository.findRateForAum(totalAum, feeMonth)).thenReturn(tierRate);
    when(vatRateProvider.getVatRate(feeMonth)).thenReturn(vatRate);

    FeeAccrual result = calculator.calculate(TUK75, date);

    assertThat(result.annualRate()).isEqualByComparingTo(new BigDecimal("0.00020"));
  }

  @Test
  void calculate_handlesLeapYear() {
    LocalDate date = LocalDate.of(2024, 2, 29);
    LocalDate feeMonth = LocalDate.of(2024, 2, 1);
    LocalDate previousMonthEnd = LocalDate.of(2024, 1, 31);
    BigDecimal assetValue = new BigDecimal("100000000");
    BigDecimal totalAum = new BigDecimal("500000000");
    BigDecimal tierRate = new BigDecimal("0.00035");
    BigDecimal vatRate = new BigDecimal("0.22");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(positionCalculationRepository.getLatestDateUpTo(TUK75, date))
        .thenReturn(Optional.of(date));
    when(positionCalculationRepository.getTotalMarketValue(TUK75, date))
        .thenReturn(Optional.of(assetValue));
    when(positionCalculationRepository.getLatestDateUpTo(previousMonthEnd))
        .thenReturn(Optional.of(previousMonthEnd));
    when(positionCalculationRepository.getTotalMarketValueAllFunds(previousMonthEnd))
        .thenReturn(Optional.of(totalAum));
    when(tierRepository.findRateForAum(totalAum, feeMonth)).thenReturn(tierRate);
    when(vatRateProvider.getVatRate(feeMonth)).thenReturn(vatRate);

    FeeAccrual result = calculator.calculate(TUK75, date);

    assertThat(result.daysInYear()).isEqualTo(366);

    BigDecimal expectedDailyNet =
        assetValue.multiply(tierRate).divide(BigDecimal.valueOf(366), 6, RoundingMode.HALF_UP);
    assertThat(result.dailyAmountNet()).isEqualByComparingTo(expectedDailyNet);
  }

  @Test
  void calculate_returnsZeroAccrualWhenNoPositionData() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(positionCalculationRepository.getLatestDateUpTo(TUK75, date)).thenReturn(Optional.empty());

    FeeAccrual result = calculator.calculate(TUK75, date);

    assertThat(result.baseValue()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountNet()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(ZERO);
  }

  @Test
  void getFeeType_returnsDepot() {
    assertThat(calculator.getFeeType()).isEqualTo(FeeType.DEPOT);
  }
}
