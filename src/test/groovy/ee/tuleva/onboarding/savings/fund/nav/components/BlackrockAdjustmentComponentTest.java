package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.SystemAccount.BLACKROCK_ADJUSTMENT;
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
class BlackrockAdjustmentComponentTest {

  @Mock private NavLedgerRepository navLedgerRepository;

  @InjectMocks private BlackrockAdjustmentComponent component;

  @Test
  void calculate_returnsPositiveBalanceAsReceivable() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 15))
            .positionReportDate(LocalDate.of(2025, 1, 15))
            .build();

    when(navLedgerRepository.getSystemAccountBalance(BLACKROCK_ADJUSTMENT.getAccountName()))
        .thenReturn(new BigDecimal("500.00"));

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo("500.00");
  }

  @Test
  void calculate_returnsNegativeBalanceAsPayable() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 15))
            .positionReportDate(LocalDate.of(2025, 1, 15))
            .build();

    when(navLedgerRepository.getSystemAccountBalance(BLACKROCK_ADJUSTMENT.getAccountName()))
        .thenReturn(new BigDecimal("-300.00"));

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo("-300.00");
  }

  @Test
  void calculate_returnsZeroWhenNoBalance() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 15))
            .positionReportDate(LocalDate.of(2025, 1, 15))
            .build();

    when(navLedgerRepository.getSystemAccountBalance(BLACKROCK_ADJUSTMENT.getAccountName()))
        .thenReturn(ZERO);

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_returnsZeroWhenNull() {
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 15))
            .positionReportDate(LocalDate.of(2025, 1, 15))
            .build();

    when(navLedgerRepository.getSystemAccountBalance(BLACKROCK_ADJUSTMENT.getAccountName()))
        .thenReturn(null);

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

  @Test
  void requiresPreliminaryNav_returnsFalse() {
    assertThat(component.requiresPreliminaryNav()).isFalse();
  }
}
