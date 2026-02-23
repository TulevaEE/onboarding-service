package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.SystemAccount.MANAGEMENT_FEE_ACCRUAL;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.LIABILITY;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagementFeeAccrualComponentTest {

  @Mock private NavLedgerRepository navLedgerRepository;

  @InjectMocks private ManagementFeeAccrualComponent component;

  @Test
  void calculate_returnsManagementFeeAccrualFromLedger() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2026, 2, 1))
            .positionReportDate(LocalDate.of(2026, 2, 1))
            .build();

    when(navLedgerRepository.getSystemAccountBalance(MANAGEMENT_FEE_ACCRUAL.getAccountName()))
        .thenReturn(new BigDecimal("-52.05"));

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo("52.05");
  }

  @Test
  void calculate_returnsZeroWhenNoLedgerBalance() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2026, 2, 1))
            .positionReportDate(LocalDate.of(2026, 2, 1))
            .build();

    when(navLedgerRepository.getSystemAccountBalance(MANAGEMENT_FEE_ACCRUAL.getAccountName()))
        .thenReturn(ZERO);

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_returnsZeroWhenLedgerBalanceIsNull() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2026, 2, 1))
            .positionReportDate(LocalDate.of(2026, 2, 1))
            .build();

    when(navLedgerRepository.getSystemAccountBalance(MANAGEMENT_FEE_ACCRUAL.getAccountName()))
        .thenReturn(null);

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo(ZERO);
  }

  @Test
  void getName_returnsManagementFeeAccrual() {
    assertThat(component.getName()).isEqualTo("management_fee_accrual");
  }

  @Test
  void getType_returnsLiability() {
    assertThat(component.getType()).isEqualTo(LIABILITY);
  }

  @Test
  void calculate_throwsWhenBalanceIsPositive() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2026, 2, 1))
            .positionReportDate(LocalDate.of(2026, 2, 1))
            .build();

    when(navLedgerRepository.getSystemAccountBalance(MANAGEMENT_FEE_ACCRUAL.getAccountName()))
        .thenReturn(new BigDecimal("52.05"));

    assertThrows(IllegalStateException.class, () -> component.calculate(context));
  }
}
