package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.investment.fees.FeeAccrual;
import ee.tuleva.onboarding.investment.fees.FeeAccrualRepository;
import ee.tuleva.onboarding.investment.fees.FeeType;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavLedgerReconciliationTest {

  @Mock private NavLedgerRepository navLedgerRepository;
  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private FeeAccrualRepository feeAccrualRepository;

  @InjectMocks private NavLedgerReconciliation reconciliation;

  @Test
  void reconcile_returnsEmptyResult_whenAllValuesMatch() {
    LocalDate date = LocalDate.of(2026, 2, 1);

    when(navLedgerRepository.getSecuritiesUnitBalances())
        .thenReturn(
            Map.of(
                "IE00BFG1TM61", new BigDecimal("1000.00000"),
                "IE00BMDBMY19", new BigDecimal("500.00000")));
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(date, TKF100, SECURITY))
        .thenReturn(
            List.of(
                securityPosition("IE00BFG1TM61", new BigDecimal("1000.00000")),
                securityPosition("IE00BMDBMY19", new BigDecimal("500.00000"))));

    when(navLedgerRepository.getSystemAccountBalance(CASH_POSITION.getAccountName()))
        .thenReturn(new BigDecimal("50000.00"));
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(date, TKF100, CASH))
        .thenReturn(List.of(position(new BigDecimal("50000.00"))));

    when(navLedgerRepository.getSystemAccountBalance(TRADE_RECEIVABLES.getAccountName()))
        .thenReturn(new BigDecimal("1000.00"));
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(date, TKF100, RECEIVABLES))
        .thenReturn(List.of(position(new BigDecimal("1000.00"))));

    when(navLedgerRepository.getSystemAccountBalance(TRADE_PAYABLES.getAccountName()))
        .thenReturn(new BigDecimal("-500.00"));
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(date, TKF100, LIABILITY))
        .thenReturn(List.of(position(new BigDecimal("-500.00"))));

    when(navLedgerRepository.getSystemAccountBalance(MANAGEMENT_FEE_ACCRUAL.getAccountName()))
        .thenReturn(new BigDecimal("-43.84"));
    when(feeAccrualRepository.findByFundAndAccrualDateAndFeeType(TKF100, date, MANAGEMENT))
        .thenReturn(Optional.of(feeAccrual(MANAGEMENT, new BigDecimal("43.84"))));

    when(navLedgerRepository.getSystemAccountBalance(DEPOT_FEE_ACCRUAL.getAccountName()))
        .thenReturn(new BigDecimal("-16.44"));
    when(feeAccrualRepository.findByFundAndAccrualDateAndFeeType(TKF100, date, DEPOT))
        .thenReturn(Optional.of(feeAccrual(DEPOT, new BigDecimal("16.44"))));

    NavLedgerReconciliation.ReconciliationResult result = reconciliation.reconcile(TKF100, date);

    assertThat(result.discrepancies()).isEmpty();
    assertThat(result.isReconciled()).isTrue();
  }

  @Test
  void reconcile_returnsDiscrepancy_whenSecuritiesUnitsDoNotMatch() {
    LocalDate date = LocalDate.of(2026, 2, 1);

    when(navLedgerRepository.getSecuritiesUnitBalances())
        .thenReturn(Map.of("IE00BFG1TM61", new BigDecimal("1000.00000")));
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(date, TKF100, SECURITY))
        .thenReturn(List.of(securityPosition("IE00BFG1TM61", new BigDecimal("1100.00000"))));

    when(navLedgerRepository.getSystemAccountBalance(CASH_POSITION.getAccountName()))
        .thenReturn(ZERO);
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(date, TKF100, CASH))
        .thenReturn(List.of());

    when(navLedgerRepository.getSystemAccountBalance(TRADE_RECEIVABLES.getAccountName()))
        .thenReturn(ZERO);
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(date, TKF100, RECEIVABLES))
        .thenReturn(List.of());

    when(navLedgerRepository.getSystemAccountBalance(TRADE_PAYABLES.getAccountName()))
        .thenReturn(ZERO);
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(date, TKF100, LIABILITY))
        .thenReturn(List.of());

    when(navLedgerRepository.getSystemAccountBalance(MANAGEMENT_FEE_ACCRUAL.getAccountName()))
        .thenReturn(ZERO);
    when(feeAccrualRepository.findByFundAndAccrualDateAndFeeType(TKF100, date, MANAGEMENT))
        .thenReturn(Optional.empty());

    when(navLedgerRepository.getSystemAccountBalance(DEPOT_FEE_ACCRUAL.getAccountName()))
        .thenReturn(ZERO);
    when(feeAccrualRepository.findByFundAndAccrualDateAndFeeType(TKF100, date, DEPOT))
        .thenReturn(Optional.empty());

    NavLedgerReconciliation.ReconciliationResult result = reconciliation.reconcile(TKF100, date);

    assertThat(result.discrepancies()).hasSize(1);
    assertThat(result.discrepancies().getFirst().component())
        .isEqualTo("securities_units:IE00BFG1TM61");
    assertThat(result.isReconciled()).isFalse();
  }

  @Test
  void reconcile_returnsMultipleDiscrepancies_whenMultipleValuesDoNotMatch() {
    LocalDate date = LocalDate.of(2026, 2, 1);

    when(navLedgerRepository.getSecuritiesUnitBalances())
        .thenReturn(Map.of("IE00BFG1TM61", new BigDecimal("1000.00000")));
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(date, TKF100, SECURITY))
        .thenReturn(List.of(securityPosition("IE00BFG1TM61", new BigDecimal("1100.00000"))));

    when(navLedgerRepository.getSystemAccountBalance(CASH_POSITION.getAccountName()))
        .thenReturn(new BigDecimal("50000.00"));
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(date, TKF100, CASH))
        .thenReturn(List.of(position(new BigDecimal("55000.00"))));

    when(navLedgerRepository.getSystemAccountBalance(TRADE_RECEIVABLES.getAccountName()))
        .thenReturn(ZERO);
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(date, TKF100, RECEIVABLES))
        .thenReturn(List.of());

    when(navLedgerRepository.getSystemAccountBalance(TRADE_PAYABLES.getAccountName()))
        .thenReturn(ZERO);
    when(fundPositionRepository.findByReportingDateAndFundAndAccountType(date, TKF100, LIABILITY))
        .thenReturn(List.of());

    when(navLedgerRepository.getSystemAccountBalance(MANAGEMENT_FEE_ACCRUAL.getAccountName()))
        .thenReturn(ZERO);
    when(feeAccrualRepository.findByFundAndAccrualDateAndFeeType(TKF100, date, MANAGEMENT))
        .thenReturn(Optional.empty());

    when(navLedgerRepository.getSystemAccountBalance(DEPOT_FEE_ACCRUAL.getAccountName()))
        .thenReturn(ZERO);
    when(feeAccrualRepository.findByFundAndAccrualDateAndFeeType(TKF100, date, DEPOT))
        .thenReturn(Optional.empty());

    NavLedgerReconciliation.ReconciliationResult result = reconciliation.reconcile(TKF100, date);

    assertThat(result.discrepancies()).hasSize(2);
    assertThat(result.isReconciled()).isFalse();
  }

  private FundPosition position(BigDecimal marketValue) {
    return FundPosition.builder().marketValue(marketValue).build();
  }

  private FundPosition securityPosition(String isin, BigDecimal quantity) {
    return FundPosition.builder().accountId(isin).quantity(quantity).build();
  }

  private FeeAccrual feeAccrual(FeeType feeType, BigDecimal dailyAmountNet) {
    return FeeAccrual.builder()
        .fund(TKF100)
        .feeType(feeType)
        .accrualDate(LocalDate.of(2026, 2, 1))
        .feeMonth(LocalDate.of(2026, 2, 1))
        .baseValue(ZERO)
        .annualRate(ZERO)
        .dailyAmountNet(dailyAmountNet)
        .dailyAmountGross(ZERO)
        .daysInYear(365)
        .build();
  }
}
