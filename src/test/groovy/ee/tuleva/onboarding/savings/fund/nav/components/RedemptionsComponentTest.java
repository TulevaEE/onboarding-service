package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
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
class RedemptionsComponentTest {

  @Mock private NavPositions navPositions;

  @InjectMocks private RedemptionsComponent component;

  @Test
  void calculate_returnsPayablesOfRedeemedUnitsFromPositionReport() {
    LocalDate reportDate = LocalDate.of(2025, 1, 15);
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 16))
            .positionReportDate(reportDate)
            .build();

    var position = new NavPosition("Payables of redeemed units", new BigDecimal("-10500.00"));
    when(navPositions.findLiabilityPositions(reportDate, TKF100)).thenReturn(List.of(position));

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo("10500.00");
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

    when(navPositions.findLiabilityPositions(reportDate, TKF100)).thenReturn(List.of());

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_negatesNegativeValue() {
    LocalDate reportDate = LocalDate.of(2025, 1, 15);
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 16))
            .positionReportDate(reportDate)
            .build();

    var position = new NavPosition("Payables of redeemed units", new BigDecimal("-138440.80"));
    when(navPositions.findLiabilityPositions(reportDate, TKF100)).thenReturn(List.of(position));

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo("138440.80");
  }

  @Test
  void calculate_throwsWhenPositiveValue() {
    LocalDate reportDate = LocalDate.of(2025, 1, 15);
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2025, 1, 16))
            .positionReportDate(reportDate)
            .build();

    var position = new NavPosition("Payables of redeemed units", new BigDecimal("138440.80"));
    when(navPositions.findLiabilityPositions(reportDate, TKF100)).thenReturn(List.of(position));

    assertThatThrownBy(() -> component.calculate(context))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getName_returnsPendingRedemptions() {
    assertThat(component.getName()).isEqualTo("pending_redemptions");
  }

  @Test
  void getType_returnsLiability() {
    assertThat(component.getType()).isEqualTo(NavComponentType.LIABILITY);
  }
}
