package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.investment.TulevaFund.TKF100;
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
class SubscriptionsComponentTest {

  @Mock private NavLedgerRepository navLedgerRepository;

  @InjectMocks private SubscriptionsComponent component;

  @Test
  void calculate_returnsSumOfCashReservedBalances() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 15))
            .positionReportDate(LocalDate.of(2025, 1, 15))
            .build();

    when(navLedgerRepository.sumBalanceByAccountName("CASH_RESERVED"))
        .thenReturn(new BigDecimal("-25000.00"));

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo("25000.00");
  }

  @Test
  void calculate_returnsZeroWhenNoPendingSubscriptions() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 15))
            .positionReportDate(LocalDate.of(2025, 1, 15))
            .build();

    when(navLedgerRepository.sumBalanceByAccountName("CASH_RESERVED")).thenReturn(ZERO);

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo(ZERO);
  }

  @Test
  void getName_returnsPendingSubscriptions() {
    assertThat(component.getName()).isEqualTo("pending_subscriptions");
  }

  @Test
  void getType_returnsAsset() {
    assertThat(component.getType()).isEqualTo(NavComponent.NavComponentType.ASSET);
  }

  @Test
  void requiresPreliminaryNav_returnsFalse() {
    assertThat(component.requiresPreliminaryNav()).isFalse();
  }
}
