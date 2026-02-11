package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.investment.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.SystemAccount.TRADE_PAYABLES;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
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
class PayablesComponentTest {

  @Mock private NavLedgerRepository navLedgerRepository;

  @InjectMocks private PayablesComponent component;

  @Test
  void calculate_returnsPayablesFromLedger() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2026, 2, 1))
            .positionReportDate(LocalDate.of(2026, 2, 1))
            .build();

    when(navLedgerRepository.getSystemAccountBalance(TRADE_PAYABLES.name()))
        .thenReturn(new BigDecimal("-5000.00"));

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo("5000.00");
  }

  @Test
  void calculate_returnsZeroWhenNoLedgerBalance() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2026, 2, 1))
            .positionReportDate(LocalDate.of(2026, 2, 1))
            .build();

    when(navLedgerRepository.getSystemAccountBalance(TRADE_PAYABLES.name())).thenReturn(ZERO);

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

    when(navLedgerRepository.getSystemAccountBalance(TRADE_PAYABLES.name())).thenReturn(null);

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo(ZERO);
  }

  @Test
  void getName_returnsPayables() {
    assertThat(component.getName()).isEqualTo("payables");
  }

  @Test
  void getType_returnsLiability() {
    assertThat(component.getType()).isEqualTo(NavComponent.NavComponentType.LIABILITY);
  }

  @Test
  void requiresPreliminaryNav_returnsFalse() {
    assertThat(component.requiresPreliminaryNav()).isFalse();
  }
}
