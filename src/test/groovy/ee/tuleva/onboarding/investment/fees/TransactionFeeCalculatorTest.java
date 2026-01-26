package ee.tuleva.onboarding.investment.fees;

import static ee.tuleva.onboarding.investment.TulevaFund.TUK75;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionFeeCalculatorTest {

  @Mock private FeeMonthResolver feeMonthResolver;

  @InjectMocks private TransactionFeeCalculator calculator;

  @Test
  void calculate_returnsZeroAccrual() {
    LocalDate date = LocalDate.of(2025, 1, 15);
    LocalDate feeMonth = LocalDate.of(2025, 1, 1);

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);

    FeeAccrual result = calculator.calculate(TUK75, date);

    assertThat(result.fund()).isEqualTo(TUK75);
    assertThat(result.feeType()).isEqualTo(FeeType.TRANSACTION);
    assertThat(result.accrualDate()).isEqualTo(date);
    assertThat(result.feeMonth()).isEqualTo(feeMonth);
    assertThat(result.baseValue()).isEqualByComparingTo(ZERO);
    assertThat(result.annualRate()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountNet()).isEqualByComparingTo(ZERO);
    assertThat(result.dailyAmountGross()).isEqualByComparingTo(ZERO);
    assertThat(result.vatRate()).isNull();
    assertThat(result.daysInYear()).isEqualTo(365);
  }

  @Test
  void calculate_handlesLeapYear() {
    LocalDate date = LocalDate.of(2024, 2, 29);
    LocalDate feeMonth = LocalDate.of(2024, 2, 1);

    when(feeMonthResolver.resolveFeeMonth(date)).thenReturn(feeMonth);

    FeeAccrual result = calculator.calculate(TUK75, date);

    assertThat(result.daysInYear()).isEqualTo(366);
  }

  @Test
  void getFeeType_returnsTransaction() {
    assertThat(calculator.getFeeType()).isEqualTo(FeeType.TRANSACTION);
  }
}
