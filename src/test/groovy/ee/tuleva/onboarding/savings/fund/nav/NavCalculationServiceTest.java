package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.investment.TulevaFund.TKF100;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.savings.fund.nav.components.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavCalculationServiceTest {

  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private NavLedgerRepository navLedgerRepository;
  @Mock private SecuritiesValueComponent securitiesValueComponent;
  @Mock private CashPositionComponent cashPositionComponent;
  @Mock private ReceivablesComponent receivablesComponent;
  @Mock private PayablesComponent payablesComponent;
  @Mock private SubscriptionsComponent subscriptionsComponent;
  @Mock private RedemptionsComponent redemptionsComponent;
  @Mock private ManagementFeeAccrualComponent managementFeeAccrualComponent;
  @Mock private DepotFeeAccrualComponent depotFeeAccrualComponent;
  @Mock private BlackrockAdjustmentComponent blackrockAdjustmentComponent;

  private NavCalculationService service;
  private Clock fixedClock;

  @BeforeEach
  void setUp() {
    fixedClock = Clock.fixed(Instant.parse("2025-01-15T14:00:00Z"), ZoneOffset.UTC);
    service =
        new NavCalculationService(
            fundPositionRepository,
            navLedgerRepository,
            securitiesValueComponent,
            cashPositionComponent,
            receivablesComponent,
            payablesComponent,
            subscriptionsComponent,
            redemptionsComponent,
            managementFeeAccrualComponent,
            depotFeeAccrualComponent,
            blackrockAdjustmentComponent,
            fixedClock);
  }

  @Test
  void calculate_performsTwoPassCalculationWithRedemptions() {
    LocalDate calcDate = LocalDate.of(2025, 1, 15);

    when(fundPositionRepository.findLatestReportingDateByFundAndAsOfDate(TKF100, calcDate))
        .thenReturn(Optional.of(calcDate));
    when(navLedgerRepository.getSystemAccountBalance("FUND_UNITS_OUTSTANDING"))
        .thenReturn(new BigDecimal("-100000.00000"));

    when(securitiesValueComponent.calculate(any())).thenReturn(new BigDecimal("900000.00"));
    when(cashPositionComponent.calculate(any())).thenReturn(new BigDecimal("50000.00"));
    when(receivablesComponent.calculate(any())).thenReturn(new BigDecimal("10000.00"));
    when(payablesComponent.calculate(any())).thenReturn(new BigDecimal("5000.00"));
    when(subscriptionsComponent.calculate(any())).thenReturn(new BigDecimal("25000.00"));
    when(managementFeeAccrualComponent.calculate(any())).thenReturn(new BigDecimal("52.08"));
    when(depotFeeAccrualComponent.calculate(any())).thenReturn(new BigDecimal("6.85"));
    when(blackrockAdjustmentComponent.calculate(any())).thenReturn(new BigDecimal("500.00"));
    when(redemptionsComponent.calculate(any())).thenReturn(new BigDecimal("10500.00"));

    NavCalculationResult result = service.calculate(TKF100, calcDate);

    assertThat(result.fund()).isEqualTo(TKF100);
    assertThat(result.calculationDate()).isEqualTo(calcDate);
    assertThat(result.securitiesValue()).isEqualByComparingTo("900000.00");
    assertThat(result.cashPosition()).isEqualByComparingTo("50000.00");
    assertThat(result.receivables()).isEqualByComparingTo("10000.00");
    assertThat(result.pendingSubscriptions()).isEqualByComparingTo("25000.00");
    assertThat(result.payables()).isEqualByComparingTo("5000.00");
    assertThat(result.managementFeeAccrual()).isEqualByComparingTo("52.08");
    assertThat(result.depotFeeAccrual()).isEqualByComparingTo("6.85");
    assertThat(result.blackrockAdjustment()).isEqualByComparingTo("500.00");
    assertThat(result.pendingRedemptions()).isEqualByComparingTo("10500.00");
    assertThat(result.unitsOutstanding()).isEqualByComparingTo("100000.00000");

    BigDecimal expectedPreliminaryNav =
        new BigDecimal("900000.00")
            .add(new BigDecimal("50000.00"))
            .add(new BigDecimal("10000.00"))
            .add(new BigDecimal("25000.00"))
            .add(new BigDecimal("500.00"))
            .subtract(new BigDecimal("5000.00"))
            .subtract(new BigDecimal("52.08"))
            .subtract(new BigDecimal("6.85"));

    BigDecimal expectedTotalNav = expectedPreliminaryNav.subtract(new BigDecimal("10500.00"));

    assertThat(result.aum()).isEqualByComparingTo(expectedTotalNav);
    assertThat(result.navPerUnit().compareTo(ZERO)).isGreaterThan(0);
  }

  @Test
  void calculate_returnsNavOfOneWhenNoUnitsOutstanding() {
    LocalDate calcDate = LocalDate.of(2025, 1, 15);

    when(fundPositionRepository.findLatestReportingDateByFundAndAsOfDate(TKF100, calcDate))
        .thenReturn(Optional.of(calcDate));
    when(navLedgerRepository.getSystemAccountBalance("FUND_UNITS_OUTSTANDING")).thenReturn(ZERO);

    when(securitiesValueComponent.calculate(any())).thenReturn(new BigDecimal("1000.00"));
    when(cashPositionComponent.calculate(any())).thenReturn(ZERO);
    when(receivablesComponent.calculate(any())).thenReturn(ZERO);
    when(payablesComponent.calculate(any())).thenReturn(ZERO);
    when(subscriptionsComponent.calculate(any())).thenReturn(ZERO);
    when(managementFeeAccrualComponent.calculate(any())).thenReturn(ZERO);
    when(depotFeeAccrualComponent.calculate(any())).thenReturn(ZERO);
    when(blackrockAdjustmentComponent.calculate(any())).thenReturn(ZERO);

    NavCalculationResult result = service.calculate(TKF100, calcDate);

    assertThat(result.navPerUnit()).isEqualByComparingTo("1");
    assertThat(result.unitsOutstanding()).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculate_throwsWhenNoPositionReport() {
    LocalDate calcDate = LocalDate.of(2025, 1, 15);

    when(fundPositionRepository.findLatestReportingDateByFundAndAsOfDate(TKF100, calcDate))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.calculate(TKF100, calcDate))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No position report found");
  }

  @Test
  void calculate_handlesNegativeBlackrockAdjustmentAsLiability() {
    LocalDate calcDate = LocalDate.of(2025, 1, 15);

    when(fundPositionRepository.findLatestReportingDateByFundAndAsOfDate(TKF100, calcDate))
        .thenReturn(Optional.of(calcDate));
    when(navLedgerRepository.getSystemAccountBalance("FUND_UNITS_OUTSTANDING"))
        .thenReturn(new BigDecimal("-100000.00000"));

    when(securitiesValueComponent.calculate(any())).thenReturn(new BigDecimal("1000000.00"));
    when(cashPositionComponent.calculate(any())).thenReturn(ZERO);
    when(receivablesComponent.calculate(any())).thenReturn(ZERO);
    when(payablesComponent.calculate(any())).thenReturn(ZERO);
    when(subscriptionsComponent.calculate(any())).thenReturn(ZERO);
    when(managementFeeAccrualComponent.calculate(any())).thenReturn(ZERO);
    when(depotFeeAccrualComponent.calculate(any())).thenReturn(ZERO);
    when(blackrockAdjustmentComponent.calculate(any())).thenReturn(new BigDecimal("-300.00"));
    when(redemptionsComponent.calculate(any())).thenReturn(ZERO);

    NavCalculationResult result = service.calculate(TKF100, calcDate);

    assertThat(result.aum()).isEqualByComparingTo("999700.00");
  }
}
