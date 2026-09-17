package ee.tuleva.onboarding.investment.fees.ocf;

import static ee.tuleva.onboarding.investment.fees.FeeType.DEPOT;
import static ee.tuleva.onboarding.investment.fees.FeeType.MANAGEMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.*;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.fees.*;
import ee.tuleva.onboarding.investment.fees.FeeChargedToFundPolicy;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavAccountLine;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculation;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OcfCalculationServiceTest {

  @Mock private FeeRateRepository feeRateRepository;
  @Mock private DepotRateResolver depotRateResolver;
  @Mock private InstrumentFeeRepository instrumentFeeRepository;
  @Mock private TransactionExecutionRepository transactionExecutionRepository;
  @Mock private OcfSnapshotRepository ocfSnapshotRepository;
  @Mock private FundNavQueryService fundNavQueryService;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private FeeChargedToFundPolicy feeChargedToFundPolicy;

  @InjectMocks private OcfCalculationService service;

  @BeforeEach
  void defaultFeesChargedToFund() {
    given(feeChargedToFundPolicy.chargedToFund(any(), any(), any())).willReturn(true);
  }

  private static final YearMonth MONTH = YearMonth.of(2026, 4);
  private static final LocalDate MONTH_END = MONTH.atEndOfMonth();
  private static final String ISIN = "XX0000000001";

  private static NavAccountLine securityLine(String isin, BigDecimal marketValue) {
    return new NavAccountLine("SECURITY", isin, isin, null, null, marketValue);
  }

  private static NavAccountLine unitsLine(BigDecimal marketValue) {
    return new NavAccountLine("UNITS", "UNITS", null, null, null, marketValue);
  }

  private void givenRate(BigDecimal netOcf) {
    given(instrumentFeeRepository.findAllValidRates(MONTH_END))
        .willReturn(List.of(InstrumentFee.builder().isin(ISIN).netOcf(netOcf).build()));
  }

  private void givenPublishedCalculation(TulevaFund fund, NavAccountLine... lines) {
    given(fundNavQueryService.findLatestPublishedNavDateOnOrBefore(fund.getCode(), MONTH_END))
        .willReturn(Optional.of(MONTH_END));
    given(fundNavQueryService.findPublishedCalculation(fund.getCode(), MONTH_END))
        .willReturn(Optional.of(new NavCalculation(Instant.EPOCH, List.of(lines))));
  }

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
  void depotFeeStaysOutOfTheOcfWhenTheFundIsNotChargedIt() {
    var fund = TUK75;
    given(feeChargedToFundPolicy.chargedToFund(fund, DEPOT, MONTH_END)).willReturn(false);
    setupManagementFee(fund, new BigDecimal("0.0034"));
    setupNoInstrumentFees();
    setupNoTransactionCosts(fund);

    var result = service.calculateOcf(fund, MONTH);

    assertThat(result.depotFeeRate()).isEqualByComparingTo(ZERO);
    assertThat(result.totalOcf()).isEqualByComparingTo(new BigDecimal("0.0034"));
    verifyNoInteractions(depotRateResolver);
  }

  @Test
  void calculateOcfWithAllFourComponents() {
    var fund = TUK75;
    setupManagementFee(fund, new BigDecimal("0.0034"));
    setupDepotFee(fund, new BigDecimal("0.0010"));

    given(instrumentFeeRepository.findAllValidRates(MONTH_END))
        .willReturn(
            List.of(InstrumentFee.builder().isin(ISIN).netOcf(new BigDecimal("0.0007")).build()));
    givenPublishedCalculation(
        fund,
        securityLine(ISIN, new BigDecimal("100000000")),
        unitsLine(new BigDecimal("100000000")));

    given(
            transactionExecutionRepository.sumCommissionsForFundAndPeriod(
                eq(fund.getCode()), any(), any()))
        .willReturn(new BigDecimal("50000"));
    given(
            fundNavQueryService.findPublishedNavDatesBetween(
                fund.getCode(), MONTH_END.minusYears(1).plusDays(1), MONTH_END))
        .willReturn(List.of(MONTH_END.minusDays(30), MONTH_END));
    given(fundNavQueryService.findAum(eq(fund.getCode()), any()))
        .willReturn(new BigDecimal("100000000"));

    var result = service.calculateOcf(fund, MONTH);

    assertThat(result.managementFeeRate()).isEqualByComparingTo(new BigDecimal("0.0034"));
    assertThat(result.depotFeeRate()).isEqualByComparingTo(new BigDecimal("0.0010"));
    assertThat(result.underlyingFundCost()).isEqualByComparingTo(new BigDecimal("0.0007"));
    assertThat(result.transactionCostRate().signum()).isGreaterThan(0);
    assertThat(result.totalOcf().signum()).isGreaterThan(0);
  }

  @Test
  void underlyingFundCostWeighsInstrumentsByShareOfTotalNav() {
    var fund = TUK75;
    setupManagementFee(fund, ZERO);
    setupDepotFee(fund, ZERO);
    setupNoTransactionCosts(fund);

    given(instrumentFeeRepository.findAllValidRates(MONTH_END))
        .willReturn(
            List.of(InstrumentFee.builder().isin(ISIN).netOcf(new BigDecimal("0.0010")).build()));
    givenPublishedCalculation(
        fund,
        securityLine(ISIN, new BigDecimal("80000000")),
        unitsLine(new BigDecimal("100000000")));

    var result = service.calculateOcf(fund, MONTH);

    // 80M of securities inside a 100M NAV: weight 0.8, not 1.0 of the securities sleeve.
    // The 20M of cash bears no underlying fund fee and correctly dilutes the cost.
    assertThat(result.underlyingFundCost()).isEqualByComparingTo(new BigDecimal("0.0008"));
  }

  @Test
  void transactionCostDividesByAverageNavAndNotBySecuritiesAlone() {
    var fund = TUK75;
    // Spans the whole trailing year, so annualisation is a no-op and this pins the denominator
    // alone: average NAV, not the securities sleeve.
    var earlier = MONTH_END.minusYears(1).plusDays(1);
    given(
            fundNavQueryService.findPublishedNavDatesBetween(
                fund.getCode(), MONTH_END.minusYears(1).plusDays(1), MONTH_END))
        .willReturn(List.of(earlier, MONTH_END));
    given(fundNavQueryService.findAum(fund.getCode(), earlier))
        .willReturn(new BigDecimal("90000000"));
    given(fundNavQueryService.findAum(fund.getCode(), MONTH_END))
        .willReturn(new BigDecimal("110000000"));
    given(
            transactionExecutionRepository.sumCommissionsForFundAndPeriod(
                eq(fund.getCode()), any(), any()))
        .willReturn(new BigDecimal("50000"));

    var rate = service.getTransactionCostRate(fund, MONTH_END);

    // 50 000 / average NAV of 100M. Dividing by the securities sleeve would inflate the rate by
    // the cash share, the same error the underlying fund cost carried.
    assertThat(rate).isEqualByComparingTo(new BigDecimal("0.0005"));
  }

  @Test
  void tkf100WeighsByPublishedNavLikeEveryOtherFund() {
    var fund = TKF100;
    setupManagementFee(fund, new BigDecimal("0.0034"));
    setupDepotFee(fund, ZERO);
    setupNoTransactionCosts(fund);

    given(instrumentFeeRepository.findAllValidRates(MONTH_END))
        .willReturn(
            List.of(
                InstrumentFee.builder()
                    .isin("XX0000000002")
                    .netOcf(new BigDecimal("0.0007"))
                    .build(),
                InstrumentFee.builder()
                    .isin("XX0000000003")
                    .netOcf(new BigDecimal("0.0016"))
                    .build()));
    givenPublishedCalculation(
        fund,
        securityLine("XX0000000002", new BigDecimal("600000")),
        securityLine("XX0000000003", new BigDecimal("400000")),
        unitsLine(new BigDecimal("1250000")));

    var result = service.calculateOcf(fund, MONTH);

    // The model portfolio assumed 100% invested: 0.60 * 0.0007 + 0.40 * 0.0016 = 0.00106.
    // Against the published NAV the fund is 80% invested, so the real weights are 0.48 and 0.32.
    assertThat(result.underlyingFundCost()).isEqualByComparingTo(new BigDecimal("0.000848"));
  }

  @Test
  void depotFeeComesFromTheSharedResolverSoItCannotDifferFromTheAccrual() {
    given(depotRateResolver.resolveAnnualRate(TUK75, MONTH_END))
        .willReturn(new BigDecimal("0.0009"));

    var rate = service.getDepotFeeRate(TUK75, MONTH_END);

    assertThat(rate).isEqualByComparingTo(new BigDecimal("0.0009"));
  }

  @Test
  void depotFeeIsZeroWhenTheResolverFindsNoRate() {
    given(depotRateResolver.resolveAnnualRate(TUV100, MONTH_END)).willReturn(ZERO);

    var rate = service.getDepotFeeRate(TUV100, MONTH_END);

    assertThat(rate).isEqualByComparingTo(ZERO);
  }

  @Test
  void depotFeeAsksForNoRateAtAllWhenTheFundDoesNotBearIt() {
    given(feeChargedToFundPolicy.chargedToFund(TUV100, DEPOT, MONTH_END)).willReturn(false);

    var rate = service.getDepotFeeRate(TUV100, MONTH_END);

    assertThat(rate).isEqualByComparingTo(ZERO);
    verifyNoInteractions(depotRateResolver);
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
  void underlyingFundCostReturnsZeroWhenNoNavDate() {
    givenRate(new BigDecimal("0.0007"));
    given(fundNavQueryService.findLatestPublishedNavDateOnOrBefore(TUK75.getCode(), MONTH_END))
        .willReturn(Optional.empty());

    var cost = service.getUnderlyingFundCost(TUK75, MONTH_END);

    assertThat(cost).isEqualByComparingTo(ZERO);
  }

  @Test
  void underlyingFundCostReturnsZeroWhenTheCalculationWasNeverPublished() {
    givenRate(new BigDecimal("0.0007"));
    given(fundNavQueryService.findLatestPublishedNavDateOnOrBefore(TUK75.getCode(), MONTH_END))
        .willReturn(Optional.of(MONTH_END));
    given(fundNavQueryService.findPublishedCalculation(TUK75.getCode(), MONTH_END))
        .willReturn(Optional.empty());

    var cost = service.getUnderlyingFundCost(TUK75, MONTH_END);

    assertThat(cost).isEqualByComparingTo(ZERO);
  }

  @Test
  void underlyingFundCostReturnsZeroWhenNoSecurityLines() {
    givenRate(new BigDecimal("0.0007"));
    givenPublishedCalculation(TUK75, unitsLine(new BigDecimal("100000000")));

    var cost = service.getUnderlyingFundCost(TUK75, MONTH_END);

    assertThat(cost).isEqualByComparingTo(ZERO);
  }

  @Test
  void underlyingFundCostReturnsZeroWhenAumIsZero() {
    givenRate(new BigDecimal("0.0007"));
    givenPublishedCalculation(
        TUK75, securityLine(ISIN, new BigDecimal("100000000")), unitsLine(ZERO));

    var cost = service.getUnderlyingFundCost(TUK75, MONTH_END);

    assertThat(cost).isEqualByComparingTo(ZERO);
  }

  @Test
  void tkf100ReturnsZeroWhenNoPublishedCalculation() {
    given(instrumentFeeRepository.findAllValidRates(MONTH_END))
        .willReturn(
            List.of(
                InstrumentFee.builder()
                    .isin("XX0000000002")
                    .netOcf(new BigDecimal("0.0007"))
                    .build()));
    given(fundNavQueryService.findLatestPublishedNavDateOnOrBefore(TKF100.getCode(), MONTH_END))
        .willReturn(Optional.empty());

    var cost = service.getUnderlyingFundCost(TKF100, MONTH_END);

    assertThat(cost).isEqualByComparingTo(ZERO);
  }

  @Test
  void aHeldInstrumentWithNoRateFailsInsteadOfCountingAsFree() {
    givenRate(new BigDecimal("0.0007"));
    givenPublishedCalculation(
        TUK75,
        securityLine(ISIN, new BigDecimal("500000")),
        securityLine("XX0000000009", new BigDecimal("500000")),
        unitsLine(new BigDecimal("1000000")));

    assertThatThrownBy(() -> service.getUnderlyingFundCost(TUK75, MONTH_END))
        .isInstanceOf(MissingInstrumentRateException.class)
        .hasMessageContaining("XX0000000009");
  }

  @Test
  void aHoldingWithNoIsinFailsTheSameWay() {
    givenRate(new BigDecimal("0.0007"));
    givenPublishedCalculation(
        TUK75,
        securityLine(ISIN, new BigDecimal("500000")),
        new NavAccountLine("SECURITY", "?", null, null, null, new BigDecimal("500000")),
        unitsLine(new BigDecimal("1000000")));

    assertThatThrownBy(() -> service.getUnderlyingFundCost(TUK75, MONTH_END))
        .isInstanceOf(MissingInstrumentRateException.class);
  }

  @Test
  void transactionCostReturnsZeroWhenNoTransactions() {
    given(
            transactionExecutionRepository.sumCommissionsForFundAndPeriod(
                eq(TUK75.getCode()), any(), any()))
        .willReturn(ZERO);

    var cost = service.getTransactionCostRate(TUK75, MONTH_END);

    assertThat(cost).isEqualByComparingTo(ZERO);
  }

  @Test
  void transactionCostWindowStartsAtTheFundsFirstPublishedNav() {
    var firstNavDate = MONTH_END.minusMonths(3);
    given(fundNavQueryService.findEarliestPublishedNavDate(TUK75.getCode()))
        .willReturn(Optional.of(firstNavDate));
    given(
            fundNavQueryService.findPublishedNavDatesBetween(
                TUK75.getCode(), MONTH_END.minusYears(1).plusDays(1), MONTH_END))
        .willReturn(List.of(firstNavDate, MONTH_END));
    given(
            transactionExecutionRepository.sumCommissionsForFundAndPeriod(
                eq(TUK75.getCode()), any(), any()))
        .willReturn(new BigDecimal("1000"));
    given(fundNavQueryService.findAum(eq(TUK75.getCode()), any()))
        .willReturn(new BigDecimal("100000000"));

    var cost = service.getTransactionCostRate(TUK75, MONTH_END);

    assertThat(cost.signum()).isGreaterThan(0);
    var zone = ZoneId.of("Europe/Tallinn");
    verify(transactionExecutionRepository)
        .sumCommissionsForFundAndPeriod(
            TUK75.getCode(),
            firstNavDate.atStartOfDay(zone).toInstant(),
            MONTH_END.plusDays(1).atStartOfDay(zone).toInstant());
  }

  @Test
  void aShortHistoryIsAnnualisedSoItCanSitBesideTheAnnualComponents() {
    var firstNavDate = MONTH_END.minusMonths(3);
    given(fundNavQueryService.findEarliestPublishedNavDate(TUK75.getCode()))
        .willReturn(Optional.of(firstNavDate));
    given(
            fundNavQueryService.findPublishedNavDatesBetween(
                TUK75.getCode(), MONTH_END.minusYears(1).plusDays(1), MONTH_END))
        .willReturn(List.of(firstNavDate, MONTH_END));
    given(
            transactionExecutionRepository.sumCommissionsForFundAndPeriod(
                eq(TUK75.getCode()), any(), any()))
        .willReturn(new BigDecimal("1000"));
    given(fundNavQueryService.findAum(eq(TUK75.getCode()), any()))
        .willReturn(new BigDecimal("100000000"));

    var cost = service.getTransactionCostRate(TUK75, MONTH_END);

    // 91 days covered. The period ratio is 1000 / 100M = 0.00001; left unscaled it would be added
    // to three components that are already annual rates, understating the fund's OCF by 91/365.
    assertThat(cost).isEqualByComparingTo(new BigDecimal("0.00004011"));
  }

  @Test
  void aPublishingGapDoesNotShortenTheWindowForAFundThatExistedThroughout() {
    var periodStart = MONTH_END.minusYears(1).plusDays(1);
    given(fundNavQueryService.findEarliestPublishedNavDate(TUK75.getCode()))
        .willReturn(Optional.of(MONTH_END.minusYears(5)));
    // Only one published NAV survives in the window. Anchoring on it would annualise a single
    // day's trading by 365; the fund's inception says it lived the whole year.
    given(fundNavQueryService.findPublishedNavDatesBetween(TUK75.getCode(), periodStart, MONTH_END))
        .willReturn(List.of(MONTH_END));
    given(
            transactionExecutionRepository.sumCommissionsForFundAndPeriod(
                eq(TUK75.getCode()), any(), any()))
        .willReturn(new BigDecimal("1000"));
    given(fundNavQueryService.findAum(eq(TUK75.getCode()), any()))
        .willReturn(new BigDecimal("100000000"));

    var cost = service.getTransactionCostRate(TUK75, MONTH_END);

    assertThat(cost).isEqualByComparingTo(new BigDecimal("0.00001"));
  }

  @Test
  void aFullYearOfHistoryIsLeftAtItsObservedRate() {
    var periodStart = MONTH_END.minusYears(1).plusDays(1);
    given(fundNavQueryService.findPublishedNavDatesBetween(TUK75.getCode(), periodStart, MONTH_END))
        .willReturn(List.of(periodStart, MONTH_END));
    given(
            transactionExecutionRepository.sumCommissionsForFundAndPeriod(
                eq(TUK75.getCode()), any(), any()))
        .willReturn(new BigDecimal("1000"));
    given(fundNavQueryService.findAum(eq(TUK75.getCode()), any()))
        .willReturn(new BigDecimal("100000000"));

    var cost = service.getTransactionCostRate(TUK75, MONTH_END);

    assertThat(cost).isEqualByComparingTo(new BigDecimal("0.00001"));
  }

  @Test
  void transactionCostReturnsZeroWhenZeroAum() {
    given(
            transactionExecutionRepository.sumCommissionsForFundAndPeriod(
                eq(TUK75.getCode()), any(), any()))
        .willReturn(new BigDecimal("1000"));
    given(
            fundNavQueryService.findPublishedNavDatesBetween(
                TUK75.getCode(), MONTH_END.minusYears(1).plusDays(1), MONTH_END))
        .willReturn(List.of());

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
        given(depotRateResolver.resolveAnnualRate(eq(fund), any())).willReturn(ZERO);
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
      lenient().when(depotRateResolver.resolveAnnualRate(eq(fund), any())).thenReturn(ZERO);
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
            Optional.of(
                new FeeRate(
                    1L,
                    fund,
                    MANAGEMENT,
                    rate,
                    FeeRateSource.FIXED,
                    MONTH_END.minusYears(1),
                    null)));
  }

  private void setupDepotFee(TulevaFund fund, BigDecimal rate) {
    given(depotRateResolver.resolveAnnualRate(fund, MONTH_END)).willReturn(rate);
  }

  private void setupNoInstrumentFees() {
    given(instrumentFeeRepository.findAllValidRates(MONTH_END)).willReturn(List.of());
  }

  private void setupNoTransactionCosts(TulevaFund fund) {
    given(
            transactionExecutionRepository.sumCommissionsForFundAndPeriod(
                eq(fund.getCode()), any(), any()))
        .willReturn(ZERO);
  }
}
