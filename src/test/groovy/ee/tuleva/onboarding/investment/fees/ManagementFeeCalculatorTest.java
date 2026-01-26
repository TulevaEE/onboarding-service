package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagementFeeCalculatorTest {

  @Mock private FeeRateRepository feeRateRepository;
  @Mock private AumRepository aumRepository;
  @Mock private FeeMonthResolver feeMonthResolver;

  @InjectMocks private ManagementFeeCalculator calculator;

  @Test
  void calculate_returnsCorrectDailyFee() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);
    BigDecimal nav = new BigDecimal("1000000000");
    BigDecimal annualRate = new BigDecimal("0.00215");

    when(aumRepository.getAumReferenceDate(TUK75, date)).thenReturn(date);
    when(aumRepository.getAum(TUK75, date)).thenReturn(Optional.of(nav));
    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, date))
        .thenReturn(
            Optional.of(new FeeRate(1L, TUK75, FeeType.MANAGEMENT, annualRate, date, null)));

    FeeAccrual result = calculator.calculate(TUK75, date);

    assertThat(result.fund()).isEqualTo(TUK75);
    assertThat(result.feeType()).isEqualTo(FeeType.MANAGEMENT);
    assertThat(result.accrualDate()).isEqualTo(date);
    assertThat(result.feeMonth()).isEqualTo(feeMonth);
    assertThat(result.baseValue()).isEqualTo(nav);
    assertThat(result.annualRate()).isEqualTo(annualRate);

    BigDecimal expectedDailyFee =
        nav.multiply(annualRate).divide(BigDecimal.valueOf(365), 6, java.math.RoundingMode.HALF_UP);
    assertThat(result.dailyAmountNet()).isEqualByComparingTo(expectedDailyFee);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(expectedDailyFee);
    assertThat(result.vatRate()).isNull();
    assertThat(result.daysInYear()).isEqualTo(365);
  }

  @Test
  void calculate_returnsZeroAccrualWhenNoAumData() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);

    when(aumRepository.getAumReferenceDate(TUK75, date)).thenReturn(null);
    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);

    FeeAccrual result = calculator.calculate(TUK75, date);

    assertThat(result.fund()).isEqualTo(TUK75);
    assertThat(result.feeType()).isEqualTo(FeeType.MANAGEMENT);
    assertThat(result.baseValue()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountNet()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_throwsWhenNoFeeRateFound() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);

    when(aumRepository.getAumReferenceDate(TUK75, date)).thenReturn(date);
    when(aumRepository.getAum(TUK75, date)).thenReturn(Optional.of(BigDecimal.TEN));
    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, date))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> calculator.calculate(TUK75, date))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void calculate_usesReferenceDate_forWeekends() {
    LocalDate saturday = LocalDate.of(2025, 1, 18);
    LocalDate friday = LocalDate.of(2025, 1, 17);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);
    BigDecimal nav = new BigDecimal("500000000");
    BigDecimal annualRate = new BigDecimal("0.002");

    when(aumRepository.getAumReferenceDate(TUK75, saturday)).thenReturn(friday);
    when(aumRepository.getAum(TUK75, friday)).thenReturn(Optional.of(nav));
    when(feeMonthResolver.resolveFeeMonth(saturday)).thenReturn(feeMonth);
    when(feeRateRepository.findValidRate(TUK75, FeeType.MANAGEMENT, friday))
        .thenReturn(
            Optional.of(new FeeRate(1L, TUK75, FeeType.MANAGEMENT, annualRate, friday, null)));

    FeeAccrual result = calculator.calculate(TUK75, saturday);

    assertThat(result.accrualDate()).isEqualTo(saturday);
    assertThat(result.referenceDate()).isEqualTo(friday);
    assertThat(result.baseValue()).isEqualTo(nav);
  }

  @Test
  void getFeeType_returnsManagement() {
    assertThat(calculator.getFeeType()).isEqualTo(FeeType.MANAGEMENT);
  }
}
