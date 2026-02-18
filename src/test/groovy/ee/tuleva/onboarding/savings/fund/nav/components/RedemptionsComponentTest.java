package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.LIABILITY;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class RedemptionsComponentTest {

  @Mock private NavLedgerRepository navLedgerRepository;

  @InjectMocks private RedemptionsComponent component;

  @Test
  void calculate_multipliesReservedUnitsByPreliminaryNav() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 15))
            .positionReportDate(LocalDate.of(2025, 1, 15))
            .build();
    context.setPreliminaryNavPerUnit(new BigDecimal("10.50000"));

    when(navLedgerRepository.sumBalanceByAccountName("FUND_UNITS_RESERVED"))
        .thenReturn(new BigDecimal("-1000.00000"));

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo("10500.00000");
  }

  @Test
  void calculate_returnsZeroWhenNoReservedUnits() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 15))
            .positionReportDate(LocalDate.of(2025, 1, 15))
            .build();
    context.setPreliminaryNavPerUnit(new BigDecimal("10.50000"));

    when(navLedgerRepository.sumBalanceByAccountName("FUND_UNITS_RESERVED")).thenReturn(ZERO);

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_throwsWhenNoPreliminaryNav() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 15))
            .positionReportDate(LocalDate.of(2025, 1, 15))
            .build();

    when(navLedgerRepository.sumBalanceByAccountName("FUND_UNITS_RESERVED"))
        .thenReturn(new BigDecimal("-1000.00000"));

    assertThatThrownBy(() -> component.calculate(context))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Preliminary NAV per unit is required for redemption calculation");
  }

  @Test
  void getName_returnsPendingRedemptions() {
    assertThat(component.getName()).isEqualTo("pending_redemptions");
  }

  @Test
  void getType_returnsLiability() {
    assertThat(component.getType()).isEqualTo(LIABILITY);
  }

  @Test
  void requiresPreliminaryNav_returnsTrue() {
    assertThat(component.requiresPreliminaryNav()).isTrue();
  }
}
