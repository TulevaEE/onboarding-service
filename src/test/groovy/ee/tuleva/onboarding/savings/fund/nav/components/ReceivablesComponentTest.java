package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.SystemAccount.TRADE_RECEIVABLES;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.ASSET;
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
class ReceivablesComponentTest {

  @Mock private NavLedgerRepository navLedgerRepository;

  @InjectMocks private ReceivablesComponent component;

  @Test
  void calculate_returnsReceivablesFromLedger() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2026, 2, 1))
            .positionReportDate(LocalDate.of(2026, 2, 1))
            .build();

    when(navLedgerRepository.getSystemAccountBalance(TRADE_RECEIVABLES.getAccountName()))
        .thenReturn(new BigDecimal("10000.00"));

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo("10000.00");
  }

  @Test
  void calculate_returnsZeroWhenNoLedgerBalance() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2026, 2, 1))
            .positionReportDate(LocalDate.of(2026, 2, 1))
            .build();

    when(navLedgerRepository.getSystemAccountBalance(TRADE_RECEIVABLES.getAccountName()))
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

    when(navLedgerRepository.getSystemAccountBalance(TRADE_RECEIVABLES.getAccountName()))
        .thenReturn(null);

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo(ZERO);
  }

  @Test
  void getName_returnsReceivables() {
    assertThat(component.getName()).isEqualTo("receivables");
  }

  @Test
  void getType_returnsAsset() {
    assertThat(component.getType()).isEqualTo(ASSET);
  }
}
