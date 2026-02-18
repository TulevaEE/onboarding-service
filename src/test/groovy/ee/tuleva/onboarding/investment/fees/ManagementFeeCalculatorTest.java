package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.investment.calculation.PositionCalculationRepository;
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
class ManagementFeeCalculatorTest {

  @Mock private FeeRateRepository feeRateRepository;
  @Mock private PositionCalculationRepository positionCalculationRepository;
  @Mock private FeeAccrualRepository feeAccrualRepository;
  @Mock private FeeMonthResolver feeMonthResolver;
  @Mock private FundPositionRepository fundPositionRepository;

  @InjectMocks private ManagementFeeCalculator calculator;

  @Test
  void calculate_returnsCorrectDailyFee() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);
    BigDecimal positionValue = new BigDecimal("1000000000");
    BigDecimal accruedFees = new BigDecimal("50000");
    BigDecimal baseValue = positionValue.add(accruedFees);
    BigDecimal annualRate = new BigDecimal("0.00215");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(positionCalculationRepository.getLatestDateUpTo(TUK75, date))
        .thenReturn(Optional.of(date));
    when(positionCalculationRepository.getTotalMarketValue(TUK75, date))
        .thenReturn(Optional.of(positionValue));
    when(feeAccrualRepository.getAccruedFeesForMonth(
            TUK75, feeMonth, List.of(MANAGEMENT, DEPOT), date))
        .thenReturn(accruedFees);
    when(feeRateRepository.findValidRate(TUK75, MANAGEMENT, date))
        .thenReturn(Optional.of(new FeeRate(1L, TUK75, MANAGEMENT, annualRate, date, null)));

    FeeAccrual result = calculator.calculate(TUK75, date);

    assertThat(result.fund()).isEqualTo(TUK75);
    assertThat(result.feeType()).isEqualTo(MANAGEMENT);
    assertThat(result.accrualDate()).isEqualTo(date);
    assertThat(result.feeMonth()).isEqualTo(feeMonth);
    assertThat(result.baseValue()).isEqualTo(baseValue);
    assertThat(result.annualRate()).isEqualTo(annualRate);

    BigDecimal expectedDailyFee =
        baseValue.multiply(annualRate).divide(BigDecimal.valueOf(365), 2, RoundingMode.HALF_UP);
    assertThat(result.dailyAmountNet()).isEqualByComparingTo(expectedDailyFee);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(expectedDailyFee);
    assertThat(result.vatRate()).isNull();
    assertThat(result.daysInYear()).isEqualTo(365);
  }

  @Test
  void calculate_returnsZeroAccrualWhenNoPositionData() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(positionCalculationRepository.getLatestDateUpTo(TUK75, date)).thenReturn(Optional.empty());

    FeeAccrual result = calculator.calculate(TUK75, date);

    assertThat(result.fund()).isEqualTo(TUK75);
    assertThat(result.feeType()).isEqualTo(MANAGEMENT);
    assertThat(result.baseValue()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountNet()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_throwsWhenNoFeeRateFound() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(positionCalculationRepository.getLatestDateUpTo(TUK75, date))
        .thenReturn(Optional.of(date));
    when(positionCalculationRepository.getTotalMarketValue(TUK75, date))
        .thenReturn(Optional.of(BigDecimal.TEN));
    when(feeAccrualRepository.getAccruedFeesForMonth(eq(TUK75), eq(feeMonth), any(), eq(date)))
        .thenReturn(ZERO);
    when(feeRateRepository.findValidRate(TUK75, MANAGEMENT, date)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> calculator.calculate(TUK75, date))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void calculate_usesReferenceDate_forWeekends() {
    LocalDate saturday = LocalDate.of(2025, 1, 18);
    LocalDate friday = LocalDate.of(2025, 1, 17);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);
    BigDecimal positionValue = new BigDecimal("500000000");
    BigDecimal annualRate = new BigDecimal("0.002");

    when(feeMonthResolver.resolveFeeMonth(saturday)).thenReturn(feeMonth);
    when(positionCalculationRepository.getLatestDateUpTo(TUK75, saturday))
        .thenReturn(Optional.of(friday));
    when(positionCalculationRepository.getTotalMarketValue(TUK75, friday))
        .thenReturn(Optional.of(positionValue));
    when(feeAccrualRepository.getAccruedFeesForMonth(
            TUK75, feeMonth, List.of(MANAGEMENT, DEPOT), saturday))
        .thenReturn(ZERO);
    when(feeRateRepository.findValidRate(TUK75, MANAGEMENT, friday))
        .thenReturn(Optional.of(new FeeRate(1L, TUK75, MANAGEMENT, annualRate, friday, null)));

    FeeAccrual result = calculator.calculate(TUK75, saturday);

    assertThat(result.accrualDate()).isEqualTo(saturday);
    assertThat(result.referenceDate()).isEqualTo(friday);
    assertThat(result.baseValue()).isEqualTo(positionValue);
  }

  @Test
  void calculate_addsAccruedFeesToBaseValue() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);
    BigDecimal positionValue = new BigDecimal("1000000000");
    BigDecimal accruedFees = new BigDecimal("100000");
    BigDecimal annualRate = new BigDecimal("0.00215");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(positionCalculationRepository.getLatestDateUpTo(TUK75, date))
        .thenReturn(Optional.of(date));
    when(positionCalculationRepository.getTotalMarketValue(TUK75, date))
        .thenReturn(Optional.of(positionValue));
    when(feeAccrualRepository.getAccruedFeesForMonth(
            TUK75, feeMonth, List.of(MANAGEMENT, DEPOT), date))
        .thenReturn(accruedFees);
    when(feeRateRepository.findValidRate(TUK75, MANAGEMENT, date))
        .thenReturn(Optional.of(new FeeRate(1L, TUK75, MANAGEMENT, annualRate, date, null)));

    FeeAccrual result = calculator.calculate(TUK75, date);

    BigDecimal expectedBase = positionValue.add(accruedFees);
    assertThat(result.baseValue()).isEqualTo(expectedBase);
  }

  @Test
  void getFeeType_returnsManagement() {
    assertThat(calculator.getFeeType()).isEqualTo(MANAGEMENT);
  }
}
