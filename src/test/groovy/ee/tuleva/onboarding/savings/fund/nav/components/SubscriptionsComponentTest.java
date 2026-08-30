package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.ASSET;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import ee.tuleva.onboarding.savings.fund.nav.NavPosition;
import ee.tuleva.onboarding.savings.fund.nav.NavPositions;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionsComponentTest {

  @Mock private NavPositions navPositions;

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

    var position = new NavPosition("Receivables of outstanding units", new BigDecimal("25000.00"));
    when(navPositions.findReceivablePositions(reportDate, TKF100)).thenReturn(List.of(position));

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

    when(navPositions.findReceivablePositions(reportDate, TKF100)).thenReturn(List.of());

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
        new NavPosition("Receivables of outstanding units", new BigDecimal("-138440.80"));
    when(navPositions.findReceivablePositions(reportDate, TKF100)).thenReturn(List.of(position));

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
