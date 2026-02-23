package ee.tuleva.onboarding.savings.fund.nav.components;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.savings.fund.nav.components.NavComponent.NavComponentType.LIABILITY;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavComponentContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepotFeeAccrualComponentTest {

  @Mock private FeeAccrualRepository feeAccrualRepository;

  @InjectMocks private DepotFeeAccrualComponent component;

  @Test
  void calculate_returnsAccruedFeeAsOfPositionReportDate() {
    var positionReportDate = LocalDate.of(2026, 2, 20);
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2026, 2, 23))
            .positionReportDate(positionReportDate)
            .build();

    when(feeAccrualRepository.getAccruedFeeAsOf(TKF100, DEPOT, positionReportDate))
        .thenReturn(new BigDecimal("16.44"));

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo("16.44");
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

    when(feeAccrualRepository.getAccruedFeeAsOf(TKF100, DEPOT, positionReportDate))
        .thenReturn(ZERO);

    BigDecimal result = component.calculate(context);

    assertThat(result).isEqualByComparingTo(ZERO);
  }

  @Test
  void getName_returnsDepotFeeAccrual() {
    assertThat(component.getName()).isEqualTo("depot_fee_accrual");
  }

  @Test
  void getType_returnsLiability() {
    assertThat(component.getType()).isEqualTo(LIABILITY);
  }

  @Test
  void calculate_throwsWhenAccruedFeeIsNegative() {
    var positionReportDate = LocalDate.of(2026, 2, 1);
    var context =
        NavComponentContext.builder()
            .fund(TKF100)
            .calculationDate(LocalDate.of(2026, 2, 1))
            .positionReportDate(positionReportDate)
            .build();

    when(feeAccrualRepository.getAccruedFeeAsOf(TKF100, DEPOT, positionReportDate))
        .thenReturn(new BigDecimal("-16.44"));

    assertThrows(IllegalStateException.class, () -> component.calculate(context));
  }
}
