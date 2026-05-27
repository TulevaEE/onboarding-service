package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.fund.TulevaFund.*;
import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.fees.*;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OcfCalculationServiceTest {

  @Mock private FeeRateRepository feeRateRepository;
  @Mock private DepotFeeTierRepository depotFeeTierRepository;
  @Mock private InstrumentFeeRepository instrumentFeeRepository;
  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  @Mock private TransactionExecutionRepository transactionExecutionRepository;
  @Mock private OcfSnapshotRepository ocfSnapshotRepository;

  @InjectMocks private OcfCalculationService service;

  private static final YearMonth MONTH = YearMonth.of(2026, 4);
  private static final LocalDate MONTH_END = MONTH.atEndOfMonth();

  @Test
  void calculateOcfSumsAllComponents() {
    var fund = TUK75;
    setupManagementFee(fund, new BigDecimal("0.0034"));
    setupDepotFee(fund, ZERO);
    setupNoInstrumentFees();
    setupNoTransactionCosts(fund);

    var result = service.calculateOcf(fund, MONTH);

    assertThat(result.managementFeeRate()).isEqualByComparingTo(new BigDecimal("0.0034"));
    assertThat(result.depotFeeRate()).isEqualByComparingTo(ZERO);
    assertThat(result.totalOcf()).isEqualByComparingTo(new BigDecimal("0.0034"));
    verify(ocfSnapshotRepository).save(any());
  }

  @Test
  void calculateOcfWithAllFourComponents() {
    var fund = TUK75;
    setupManagementFee(fund, new BigDecimal("0.0034"));
    setupDepotFee(fund, new BigDecimal("0.0010"));

    given(instrumentFeeRepository.findAllValidRates(MONTH_END))
        .willReturn(
            List.of(
                InstrumentFee.builder()
                    .isin("IE00BFNM3G45")
                    .netOcf(new BigDecimal("0.0007"))
                    .build()));
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, MONTH_END))
        .willReturn(Optional.of(MONTH_END));
    var position = mock(FundPosition.class);
    given(position.getMarketValue()).willReturn(new BigDecimal("100000000"));
    given(position.getAccountId()).willReturn("IE00BFNM3G45");
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(MONTH_END, fund, SECURITY))
        .willReturn(List.of(position));

    given(
            transactionExecutionRepository.sumCommissionsForFundAndPeriod(
                eq(fund.getCode()), any(), eq(MONTH_END)))
        .willReturn(new BigDecimal("50000"));
    given(fundPositionRepository.findDistinctNavDatesByFund(fund))
        .willReturn(List.of(MONTH_END.minusDays(30), MONTH_END));
    given(
            fundPositionRepository.sumMarketValueByFundAndAccountTypes(
                eq(fund), any(), eq(List.of(SECURITY))))
        .willReturn(new BigDecimal("100000000"));

    var result = service.calculateOcf(fund, MONTH);

    assertThat(result.managementFeeRate()).isEqualByComparingTo(new BigDecimal("0.0034"));
    assertThat(result.depotFeeRate()).isEqualByComparingTo(new BigDecimal("0.0010"));
    assertThat(result.underlyingFundCost()).isEqualByComparingTo(new BigDecimal("0.0007"));
    assertThat(result.transactionCostRate().signum()).isGreaterThan(0);
    assertThat(result.totalOcf().signum()).isGreaterThan(0);
  }

  @Test
  void tkf100UsesModelPortfolioForUnderlyingCost() {
    var fund = TKF100;
    setupManagementFee(fund, new BigDecimal("0.0034"));
    setupDepotFee(fund, ZERO);
    setupNoTransactionCosts(fund);

    given(instrumentFeeRepository.findAllValidRates(MONTH_END))
        .willReturn(
            List.of(
                InstrumentFee.builder()
                    .isin("IE00BJZ2DC62")
                    .netOcf(new BigDecimal("0.0007"))
                    .build(),
                InstrumentFee.builder()
                    .isin("IE00BMDBMY19")
                    .netOcf(new BigDecimal("0.0016"))
                    .build()));

    var alloc1 = mock(ModelPortfolioAllocation.class);
    given(alloc1.getIsin()).willReturn("IE00BJZ2DC62");
    given(alloc1.getWeight()).willReturn(new BigDecimal("0.60"));
    var alloc2 = mock(ModelPortfolioAllocation.class);
    given(alloc2.getIsin()).willReturn("IE00BMDBMY19");
    given(alloc2.getWeight()).willReturn(new BigDecimal("0.40"));

    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(fund, MONTH_END))
        .willReturn(List.of(alloc1, alloc2));

    var result = service.calculateOcf(fund, MONTH);

    // 0.60 * 0.0007 + 0.40 * 0.0016 = 0.00042 + 0.00064 = 0.00106
    assertThat(result.underlyingFundCost()).isEqualByComparingTo(new BigDecimal("0.00106"));
  }

  @Test
  void depotFeeUsesRateTableFirst() {
    var rate = service.getDepotFeeRate(TUK75, MONTH_END);

    given(feeRateRepository.findValidRate(TUK75, DEPOT, MONTH_END))
        .willReturn(
            Optional.of(new FeeRate(1L, TUK75, DEPOT, ZERO, MONTH_END.minusYears(1), null)));

    rate = service.getDepotFeeRate(TUK75, MONTH_END);
    assertThat(rate).isEqualByComparingTo(ZERO);
  }

  @Test
  void depotFeeFallsBackToTierWhenNoRateExists() {
    given(feeRateRepository.findValidRate(TUV100, DEPOT, MONTH_END)).willReturn(Optional.empty());
    given(fundPositionRepository.findLatestSecurityNavDateUpTo(MONTH_END))
        .willReturn(Optional.of(MONTH_END));
    given(fundPositionRepository.sumSecurityMarketValueAllFunds(MONTH_END))
        .willReturn(new BigDecimal("200000000"));
    given(depotFeeTierRepository.findRateForAum(new BigDecimal("200000000"), MONTH_END))
        .willReturn(new BigDecimal("0.0010"));

    var rate = service.getDepotFeeRate(TUV100, MONTH_END);

    assertThat(rate).isEqualByComparingTo(new BigDecimal("0.0010"));
  }

  @Test
  void managementFeeReturnsZeroWhenNotFound() {
    given(feeRateRepository.findValidRate(TUK75, MANAGEMENT, MONTH_END))
        .willReturn(Optional.empty());

    var rate = service.getManagementFeeRate(TUK75, MONTH_END);

    assertThat(rate).isEqualByComparingTo(ZERO);
  }

  @Test
  void underlyingFundCostReturnsZeroWhenNoRates() {
    given(instrumentFeeRepository.findAllValidRates(MONTH_END)).willReturn(List.of());

    var cost = service.getUnderlyingFundCost(TUK75, MONTH_END);

    assertThat(cost).isEqualByComparingTo(ZERO);
  }

  @Test
  void underlyingFundCostReturnsZeroWhenNoPositionNavDate() {
    given(instrumentFeeRepository.findAllValidRates(MONTH_END))
        .willReturn(
            List.of(
                InstrumentFee.builder()
                    .isin("IE00BFNM3G45")
                    .netOcf(new BigDecimal("0.0007"))
                    .build()));
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, MONTH_END))
        .willReturn(Optional.empty());

    var cost = service.getUnderlyingFundCost(TUK75, MONTH_END);

    assertThat(cost).isEqualByComparingTo(ZERO);
  }

  @Test
  void underlyingFundCostReturnsZeroWhenNoPositions() {
    given(instrumentFeeRepository.findAllValidRates(MONTH_END))
        .willReturn(
            List.of(
                InstrumentFee.builder()
                    .isin("IE00BFNM3G45")
                    .netOcf(new BigDecimal("0.0007"))
                    .build()));
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, MONTH_END))
        .willReturn(Optional.of(MONTH_END));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(MONTH_END, TUK75, SECURITY))
        .willReturn(List.of());

    var cost = service.getUnderlyingFundCost(TUK75, MONTH_END);

    assertThat(cost).isEqualByComparingTo(ZERO);
  }

  @Test
  void underlyingFundCostReturnsZeroWhenZeroTotalValue() {
    given(instrumentFeeRepository.findAllValidRates(MONTH_END))
        .willReturn(
            List.of(
                InstrumentFee.builder()
                    .isin("IE00BFNM3G45")
                    .netOcf(new BigDecimal("0.0007"))
                    .build()));
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, MONTH_END))
        .willReturn(Optional.of(MONTH_END));
    var position = mock(FundPosition.class);
    given(position.getMarketValue()).willReturn(ZERO);
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(MONTH_END, TUK75, SECURITY))
        .willReturn(List.of(position));

    var cost = service.getUnderlyingFundCost(TUK75, MONTH_END);

    assertThat(cost).isEqualByComparingTo(ZERO);
  }

  @Test
  void tkf100ReturnsZeroWhenNoModelAllocations() {
    given(instrumentFeeRepository.findAllValidRates(MONTH_END))
        .willReturn(
            List.of(
                InstrumentFee.builder()
                    .isin("IE00BJZ2DC62")
                    .netOcf(new BigDecimal("0.0007"))
                    .build()));
    given(modelPortfolioAllocationRepository.findLatestByFundAsOf(TKF100, MONTH_END))
        .willReturn(List.of());

    var cost = service.getUnderlyingFundCost(TKF100, MONTH_END);

    assertThat(cost).isEqualByComparingTo(ZERO);
  }

  @Test
  void transactionCostReturnsZeroWhenNoTransactions() {
    given(
            transactionExecutionRepository.sumCommissionsForFundAndPeriod(
                eq(TUK75.getCode()), any(), eq(MONTH_END)))
        .willReturn(ZERO);

    var cost = service.getTransactionCostRate(TUK75, MONTH_END);

    assertThat(cost).isEqualByComparingTo(ZERO);
  }

  @Test
  void transactionCostReturnsZeroWhenZeroAum() {
    given(
            transactionExecutionRepository.sumCommissionsForFundAndPeriod(
                eq(TUK75.getCode()), any(), eq(MONTH_END)))
        .willReturn(new BigDecimal("1000"));
    given(fundPositionRepository.findDistinctNavDatesByFund(TUK75)).willReturn(List.of());

    var cost = service.getTransactionCostRate(TUK75, MONTH_END);

    assertThat(cost).isEqualByComparingTo(ZERO);
  }

  @Test
  void calculateForAllFundsIsolatesErrors() {
    for (var fund : TulevaFund.values()) {
      if (fund == TUK75) {
        given(feeRateRepository.findValidRate(eq(fund), eq(MANAGEMENT), any()))
            .willThrow(new RuntimeException("test error"));
      } else {
        given(feeRateRepository.findValidRate(eq(fund), eq(MANAGEMENT), any()))
            .willReturn(Optional.empty());
        given(feeRateRepository.findValidRate(eq(fund), eq(DEPOT), any()))
            .willReturn(
                Optional.of(new FeeRate(1L, fund, DEPOT, ZERO, MONTH_END.minusYears(1), null)));
        given(
                transactionExecutionRepository.sumCommissionsForFundAndPeriod(
                    eq(fund.getCode()), any(), any()))
            .willReturn(ZERO);
      }
    }
    given(instrumentFeeRepository.findAllValidRates(any())).willReturn(List.of());

    service.calculateForAllFunds(MONTH);

    verify(ocfSnapshotRepository, times(TulevaFund.values().length - 1)).save(any());
  }

  @Test
  void backfillMonthsComputesMultipleMonths() {
    var clock =
        Clock.fixed(
            MONTH.atDay(15).atStartOfDay(ZoneId.of("Europe/Tallinn")).toInstant(),
            ZoneId.of("Europe/Tallinn"));

    for (var fund : TulevaFund.values()) {
      lenient()
          .when(feeRateRepository.findValidRate(eq(fund), eq(MANAGEMENT), any()))
          .thenReturn(Optional.empty());
      lenient()
          .when(feeRateRepository.findValidRate(eq(fund), eq(DEPOT), any()))
          .thenReturn(
              Optional.of(new FeeRate(1L, fund, DEPOT, ZERO, MONTH_END.minusYears(1), null)));
      lenient().when(instrumentFeeRepository.findAllValidRates(any())).thenReturn(List.of());
      lenient()
          .when(
              transactionExecutionRepository.sumCommissionsForFundAndPeriod(
                  eq(fund.getCode()), any(), any()))
          .thenReturn(ZERO);
    }

    service.backfillMonths(3, clock);

    // 3 months * 4 funds = 12 saves
    verify(ocfSnapshotRepository, times(3 * TulevaFund.values().length)).save(any());
  }

  @Test
  void ocfSnapshotFromResultSetMapsAllFields() throws SQLException {
    var rs = mock(ResultSet.class);
    given(rs.getLong("id")).willReturn(42L);
    given(rs.getString("fund_code")).willReturn("TUK75");
    given(rs.getDate("snapshot_month")).willReturn(Date.valueOf("2026-04-01"));
    given(rs.getBigDecimal("management_fee_rate")).willReturn(new BigDecimal("0.0034"));
    given(rs.getBigDecimal("depot_fee_rate")).willReturn(new BigDecimal("0.0010"));
    given(rs.getBigDecimal("underlying_fund_cost")).willReturn(new BigDecimal("0.0007"));
    given(rs.getBigDecimal("transaction_cost_rate")).willReturn(new BigDecimal("0.0002"));
    given(rs.getBigDecimal("total_ocf")).willReturn(new BigDecimal("0.0053"));

    var snapshot = OcfSnapshot.fromResultSet(rs, 1);

    assertThat(snapshot.id()).isEqualTo(42L);
    assertThat(snapshot.fundCode()).isEqualTo("TUK75");
    assertThat(snapshot.snapshotMonth()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(snapshot.managementFeeRate()).isEqualByComparingTo(new BigDecimal("0.0034"));
    assertThat(snapshot.depotFeeRate()).isEqualByComparingTo(new BigDecimal("0.0010"));
    assertThat(snapshot.underlyingFundCost()).isEqualByComparingTo(new BigDecimal("0.0007"));
    assertThat(snapshot.transactionCostRate()).isEqualByComparingTo(new BigDecimal("0.0002"));
    assertThat(snapshot.totalOcf()).isEqualByComparingTo(new BigDecimal("0.0053"));
  }

  private void setupManagementFee(TulevaFund fund, BigDecimal rate) {
    given(feeRateRepository.findValidRate(fund, MANAGEMENT, MONTH_END))
        .willReturn(
            Optional.of(new FeeRate(1L, fund, MANAGEMENT, rate, MONTH_END.minusYears(1), null)));
  }

  private void setupDepotFee(TulevaFund fund, BigDecimal rate) {
    given(feeRateRepository.findValidRate(fund, DEPOT, MONTH_END))
        .willReturn(Optional.of(new FeeRate(1L, fund, DEPOT, rate, MONTH_END.minusYears(1), null)));
  }

  private void setupNoInstrumentFees() {
    given(instrumentFeeRepository.findAllValidRates(MONTH_END)).willReturn(List.of());
  }

  private void setupNoTransactionCosts(TulevaFund fund) {
    given(
            transactionExecutionRepository.sumCommissionsForFundAndPeriod(
                eq(fund.getCode()), any(), eq(MONTH_END)))
        .willReturn(ZERO);
  }
}
