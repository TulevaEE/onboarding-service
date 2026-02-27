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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagementFeeAccrualComponentTest {

  private static final ZoneId ESTONIAN_ZONE = ZoneId.of("Europe/Tallinn");

  @Mock private NavLedgerRepository navLedgerRepository;

  @InjectMocks private ManagementFeeAccrualComponent component;

  @Test
  void calculate_returnsLedgerBalanceWithFeeCutoff() {
    var positionReportDate = LocalDate.of(2026, 2, 20);
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2026, 2, 23))
            .positionReportDate(positionReportDate)
            .build();

    Instant feeCutoff =
        positionReportDate.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();
    when(navLedgerRepository.getSystemAccountBalanceBefore(
            MANAGEMENT_FEE_ACCRUAL.getAccountName(), feeCutoff))
        .thenReturn(new BigDecimal("-52.05"));

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo("52.05");
  }

  @Test
  void calculate_returnsZeroWhenNoAccruedFees() {
    var positionReportDate = LocalDate.of(2026, 2, 1);
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2026, 2, 1))
            .positionReportDate(positionReportDate)
            .build();

    Instant feeCutoff =
        positionReportDate.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();
    when(navLedgerRepository.getSystemAccountBalanceBefore(
            MANAGEMENT_FEE_ACCRUAL.getAccountName(), feeCutoff))
        .thenReturn(ZERO);

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
    var positionReportDate = LocalDate.of(2026, 2, 1);
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2026, 2, 1))
            .positionReportDate(positionReportDate)
            .build();

    Instant feeCutoff =
        positionReportDate.plusDays(1).atStartOfDay().atZone(ESTONIAN_ZONE).toInstant();
    when(navLedgerRepository.getSystemAccountBalanceBefore(
            MANAGEMENT_FEE_ACCRUAL.getAccountName(), feeCutoff))
        .thenReturn(new BigDecimal("52.05"));

    assertThrows(IllegalStateException.class, () -> component.calculate(context));
  }
}
