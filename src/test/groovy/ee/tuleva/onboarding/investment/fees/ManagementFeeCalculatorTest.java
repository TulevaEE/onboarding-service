package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

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

    given(feeMonthResolver.resolveFeeMonth(date)).willReturn(LocalDate.of(2025, 1, 1));
    given(feeRateRepository.findValidRate(TUK75, MANAGEMENT, date))
        .willReturn(
            Optional.of(
                new FeeRate(1L, TUK75, MANAGEMENT, annualRate, FeeRateSource.FIXED, date, null)));

    FeeAccrual result = calculator.calculate(TUK75, date, new FeeBases(baseValue, baseValue));

    assertThat(result.fund()).isEqualTo(TUK75);
    assertThat(result.feeType()).isEqualTo(MANAGEMENT);
    assertThat(result.accrualDate()).isEqualTo(date);
    assertThat(result.feeMonth()).isEqualTo(LocalDate.of(2025, 1, 1));
    assertThat(result.baseValue()).isEqualTo(baseValue);
    assertThat(result.annualRate()).isEqualTo(annualRate);
    assertThat(result.referenceDate()).isEqualTo(date);

    BigDecimal expectedDailyFee =
        baseValue.multiply(annualRate).divide(BigDecimal.valueOf(365), 6, RoundingMode.HALF_UP);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(expectedDailyFee);
    assertThat(result.daysInYear()).isEqualTo(365);
  }

  @Test
  void calculate_throwsWhenNoFeeRateFound() {
    LocalDate date = LocalDate.of(2025, 1, 15);

    given(feeMonthResolver.resolveFeeMonth(date)).willReturn(LocalDate.of(2025, 1, 1));
    given(feeRateRepository.findValidRate(TUK75, MANAGEMENT, date)).willReturn(Optional.empty());

    assertThatThrownBy(
            () -> calculator.calculate(TUK75, date, new FeeBases(BigDecimal.TEN, BigDecimal.TEN)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getFeeType_returnsManagement() {
    assertThat(calculator.getFeeType()).isEqualTo(MANAGEMENT);
  }
}
