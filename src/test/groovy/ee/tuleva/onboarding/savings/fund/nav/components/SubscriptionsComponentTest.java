package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.position.AccountType.RECEIVABLES;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.ASSET;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionsComponentTest {

  @Mock private FundPositionRepository fundPositionRepository;

  @InjectMocks private SubscriptionsComponent component;

  @Test
  void calculate_returnsReceivablesOfOutstandingUnitsFromPositionReport() {
    LocalDate reportDate = LocalDate.of(2025, 1, 15);
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 16))
            .positionReportDate(reportDate)
            .build();

    var position =
        FundPosition.builder()
            .navDate(reportDate)
            .fund(TKF100)
            .accountType(RECEIVABLES)
            .accountId(TKF100.getIsin())
            .marketValue(new BigDecimal("25000.00"))
            .build();
    when(fundPositionRepository.findByNavDateAndFundAndAccountTypeAndAccountId(
            reportDate, TKF100, RECEIVABLES, TKF100.getIsin()))
        .thenReturn(Optional.of(position));

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo("25000.00");
  }

  @Test
  void calculate_returnsZeroWhenNoPositionReportEntry() {
    LocalDate reportDate = LocalDate.of(2025, 1, 15);
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 16))
            .positionReportDate(reportDate)
            .build();

    when(fundPositionRepository.findByNavDateAndFundAndAccountTypeAndAccountId(
            reportDate, TKF100, RECEIVABLES, TKF100.getIsin()))
        .thenReturn(Optional.empty());

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_throwsWhenNegativeValue() {
    LocalDate reportDate = LocalDate.of(2025, 1, 15);
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 16))
            .positionReportDate(reportDate)
            .build();

    var position =
        FundPosition.builder()
            .navDate(reportDate)
            .fund(TKF100)
            .accountType(RECEIVABLES)
            .accountId(TKF100.getIsin())
            .marketValue(new BigDecimal("-100.00"))
            .build();
    when(fundPositionRepository.findByNavDateAndFundAndAccountTypeAndAccountId(
            reportDate, TKF100, RECEIVABLES, TKF100.getIsin()))
        .thenReturn(Optional.of(position));

    assertThatThrownBy(() -> component.calculate(context))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getName_returnsPendingSubscriptions() {
    assertThat(component.getName()).isEqualTo("pending_subscriptions");
  }

  @Test
  void getType_returnsAsset() {
    assertThat(component.getType()).isEqualTo(ASSET);
  }
}
