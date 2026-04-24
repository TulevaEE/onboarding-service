package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
class ManagementFeeCalculatorTest {

  @Mock private FeeRateRepository feeRateRepository;
  @Mock private FeeMonthResolver feeMonthResolver;

  @InjectMocks private ManagementFeeCalculator calculator;

  @Test
  void calculate_returnsCorrectDailyFee() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    BigDecimal baseValue = new BigDecimal("1000000000");
    BigDecimal annualRate = new BigDecimal("0.02");

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(LocalDate.of(2025, 1, 1));
    when(feeRateRepository.findValidRate(TUK75, MANAGEMENT, date))
        .thenReturn(Optional.of(new FeeRate(1L, TUK75, MANAGEMENT, annualRate, date, null)));

    FeeAccrual result = calculator.calculate(TUK75, date, baseValue);

    assertThat(result.fund()).isEqualTo(TUK75);
    assertThat(result.feeType()).isEqualTo(MANAGEMENT);
    assertThat(result.accrualDate()).isEqualTo(date);
    assertThat(result.feeMonth()).isEqualTo(LocalDate.of(2025, 1, 1));
    assertThat(result.baseValue()).isEqualTo(baseValue);
    assertThat(result.annualRate()).isEqualTo(annualRate);
    assertThat(result.referenceDate()).isEqualTo(date);

    BigDecimal expectedDailyFee =
        baseValue.multiply(annualRate).divide(BigDecimal.valueOf(365), 6, RoundingMode.HALF_UP);
    assertThat(result.dailyAmountNet()).isEqualByComparingTo(expectedDailyFee);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(expectedDailyFee);
    assertThat(result.vatRate()).isNull();
    assertThat(result.daysInYear()).isEqualTo(365);
  }

  @Test
  void calculate_throwsWhenNoFeeRateFound() {
    LocalDate date = LocalDate.of(2025, 1, 15);

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(LocalDate.of(2025, 1, 1));
    when(feeRateRepository.findValidRate(TUK75, MANAGEMENT, date)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> calculator.calculate(TUK75, date, BigDecimal.TEN))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getFeeType_returnsManagement() {
    assertThat(calculator.getFeeType()).isEqualTo(MANAGEMENT);
  }
}
