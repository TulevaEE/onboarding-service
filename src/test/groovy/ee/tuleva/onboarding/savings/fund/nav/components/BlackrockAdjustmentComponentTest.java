package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.systemAccountWithBalance;
import static ee.tuleva.onboarding.ledger.SystemAccount.BLACKROCK_ADJUSTMENT;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.ASSET;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlackrockAdjustmentComponentTest {

  @Mock private LedgerService ledgerService;

  @InjectMocks private BlackrockAdjustmentComponent component;

  private static final Instant CUTOFF = Instant.parse("2025-01-15T14:00:00Z");

  @Test
  void calculate_returnsBalanceAtCutoff() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 15))
            .positionReportDate(LocalDate.of(2025, 1, 14))
            .cutoff(CUTOFF)
            .build();

    when(ledgerService.getSystemAccount(BLACKROCK_ADJUSTMENT))
        .thenReturn(systemAccountWithBalance(new BigDecimal("500.00"), CUTOFF.minusSeconds(1)));

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo("500.00");
  }

  @Test
  void calculate_returnsNegativeBalance() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 15))
            .positionReportDate(LocalDate.of(2025, 1, 14))
            .cutoff(CUTOFF)
            .build();

    when(ledgerService.getSystemAccount(BLACKROCK_ADJUSTMENT))
        .thenReturn(systemAccountWithBalance(new BigDecimal("-300.00"), CUTOFF.minusSeconds(1)));

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo("-300.00");
  }

  @Test
  void calculate_returnsZeroWhenNoEntries() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 15))
            .positionReportDate(LocalDate.of(2025, 1, 14))
            .cutoff(CUTOFF)
            .build();

    when(ledgerService.getSystemAccount(BLACKROCK_ADJUSTMENT))
        .thenReturn(systemAccountWithBalance(ZERO));

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo(ZERO);
  }

  @Test
  void getName_returnsBlackrockAdjustment() {
    assertThat(component.getName()).isEqualTo("blackrock_adjustment");
  }

  @Test
  void getType_returnsAsset() {
    assertThat(component.getType()).isEqualTo(ASSET);
  }
}
