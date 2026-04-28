package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static ee.tuleva.onboarding.investment.check.limit.CheckType.*;
import static ee.tuleva.onboarding.investment.portfolio.Provider.ISHARES;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.lenient;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.portfolio.*;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LimitCheckServiceTest {

  @Mock FundPositionRepository fundPositionRepository;
  @Mock PositionLimitRepository positionLimitRepository;
  @Mock ProviderLimitRepository providerLimitRepository;
  @Mock FundLimitRepository fundLimitRepository;
  @Mock ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  @Mock LimitCheckEventRepository limitCheckEventRepository;
  @Mock PositionLimitChecker positionLimitChecker;
  @Mock ProviderLimitChecker providerLimitChecker;
  @Mock ReserveLimitChecker reserveLimitChecker;
  @Mock FreeCashLimitChecker freeCashLimitChecker;
  @Mock FundValueProvider fundValueProvider;
  @Mock NavReportPositionProvider navReportPositionProvider;
  @Mock TransactionOrderRepository transactionOrderRepository;

  Clock clock = Clock.fixed(Instant.parse("2026-03-04T16:00:00Z"), ZoneId.of("Europe/Tallinn"));

  @InjectMocks LimitCheckService service;

  @Test
  void delegatesToAllThreeCheckers() {
    service = createService();
    var today = LocalDate.of(2026, 3, 4);
    var fund = TUK75;
    var position =
        FundPosition.builder()
            .accountId("IE001")
            .fund(fund)
            .marketValue(new BigDecimal("100000"))
            .build();
    var nonSecurityNav = new BigDecimal("100000");
    var securitiesNav = new BigDecimal("900000");
    var totalNav = new BigDecimal("1000000");

    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, today))
        .thenReturn(Optional.of(today));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(today, fund, SECURITY))
        .thenReturn(List.of(position));
    when(navReportPositionProvider.getCalculatedAum(fund, today))
        .thenReturn(Optional.of(totalNav));

    var cashPosition =
        FundPosition.builder().marketValue(new BigDecimal("80000")).fund(fund).build();
    var liabilityPosition =
        FundPosition.builder().marketValue(new BigDecimal("20000")).fund(fund).build();
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(today, fund, CASH))
        .thenReturn(List.of(cashPosition));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(today, fund, LIABILITY))
        .thenReturn(List.of(liabilityPosition));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(today, fund, FEE))
        .thenReturn(List.of());

    var positionLimit =
        PositionLimit.builder()
            .isin("IE001")
            .fund(fund)
            .softLimitPercent(new BigDecimal("15"))
            .hardLimitPercent(new BigDecimal("20"))
            .build();
    when(positionLimitRepository.findLatestByFundAsOf(fund, today))
        .thenReturn(List.of(positionLimit));

    var providerLimit =
        ProviderLimit.builder()
            .provider(ISHARES)
            .fund(fund)
            .softLimitPercent(new BigDecimal("30"))
            .hardLimitPercent(new BigDecimal("40"))
            .build();
    when(providerLimitRepository.findLatestByFundAsOf(fund, today))
        .thenReturn(List.of(providerLimit));

    var fundLimit =
        FundLimit.builder()
            .fund(fund)
            .reserveSoft(new BigDecimal("50000"))
            .reserveHard(new BigDecimal("30000"))
            .build();
    when(fundLimitRepository.findLatestByFundAsOf(fund, today)).thenReturn(Optional.of(fundLimit));

    var allocation =
        ModelPortfolioAllocation.builder()
            .isin("IE001")
            .fund(fund)
            .provider(ISHARES)
            .weight(BigDecimal.ONE)
            .build();
    when(modelPortfolioAllocationRepository.findLatestByFund(fund)).thenReturn(List.of(allocation));

    var positionBreach =
        new PositionBreach(
            fund,
            "IE001",
            "iShares",
            new BigDecimal("10"),
            new BigDecimal("15"),
            new BigDecimal("20"),
            OK);
    when(positionLimitChecker.check(eq(fund), eq(List.of(position)), eq(totalNav), anyList()))
        .thenReturn(List.of(positionBreach));

    var providerBreach =
        new ProviderBreach(
            fund, ISHARES, new BigDecimal("10"), new BigDecimal("30"), new BigDecimal("40"), OK);
    when(providerLimitChecker.check(eq(fund), anyList(), eq(totalNav), anyMap(), anyList()))
        .thenReturn(List.of(providerBreach));

    var reserveBreach =
        new ReserveBreach(
            fund, new BigDecimal("80000"), new BigDecimal("50000"), new BigDecimal("30000"), OK);
    when(reserveLimitChecker.check(eq(fund), eq(new BigDecimal("80000")), eq(fundLimit)))
        .thenReturn(reserveBreach);

    var results = service.runChecks();

    var tuk75Result = results.stream().filter(r -> r.fund() == TUK75).findFirst().orElseThrow();
    assertThat(tuk75Result.positionBreaches()).containsExactly(positionBreach);
    assertThat(tuk75Result.providerBreaches()).containsExactly(providerBreach);
    assertThat(tuk75Result.reserveBreach()).isEqualTo(reserveBreach);

    verify(limitCheckEventRepository, atLeast(4)).save(any(LimitCheckEvent.class));
  }

  @Test
  void handlesNoPositionData() {
    service = createService();
    var today = LocalDate.of(2026, 3, 4);

    for (var fund : TulevaFund.values()) {
      when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, today))
          .thenReturn(Optional.empty());
    }

    var results = service.runChecks();

    assertThat(results).isEmpty();
    verify(limitCheckEventRepository, never()).save(any());
  }

  @Test
  void computesCashBalanceCorrectly() {
    service = createService();
    var today = LocalDate.of(2026, 3, 4);
    var fund = TUK75;

    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, today))
        .thenReturn(Optional.of(today));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(today, fund, SECURITY))
        .thenReturn(List.of());
    when(navReportPositionProvider.getCalculatedAum(fund, today))
        .thenReturn(Optional.of(BigDecimal.ZERO));

    var cash1 = FundPosition.builder().marketValue(new BigDecimal("50000")).fund(fund).build();
    var cash2 = FundPosition.builder().marketValue(new BigDecimal("30000")).fund(fund).build();
    var liability = FundPosition.builder().marketValue(new BigDecimal("10000")).fund(fund).build();
    var feeAccrual = FundPosition.builder().marketValue(new BigDecimal("-5000")).fund(fund).build();
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(today, fund, CASH))
        .thenReturn(List.of(cash1, cash2));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(today, fund, LIABILITY))
        .thenReturn(List.of(liability));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(today, fund, FEE))
        .thenReturn(List.of(feeAccrual));

    when(positionLimitRepository.findLatestByFundAsOf(fund, today)).thenReturn(List.of());
    when(providerLimitRepository.findLatestByFundAsOf(fund, today)).thenReturn(List.of());
    var fundLimit =
        FundLimit.builder()
            .fund(fund)
            .reserveSoft(new BigDecimal("50000"))
            .reserveHard(new BigDecimal("30000"))
            .build();
    when(fundLimitRepository.findLatestByFundAsOf(fund, today)).thenReturn(Optional.of(fundLimit));
    when(modelPortfolioAllocationRepository.findLatestByFund(fund)).thenReturn(List.of());
    when(positionLimitChecker.check(any(), any(), any(), any())).thenReturn(List.of());
    when(providerLimitChecker.check(any(), any(), any(), any(), any())).thenReturn(List.of());

    when(reserveLimitChecker.check(fund, new BigDecimal("80000"), fundLimit)).thenReturn(null);

    service.runChecks();

    verify(reserveLimitChecker).check(fund, new BigDecimal("80000"), fundLimit);
    // liabilityTotal = 10000 + (-5000) = 5000
    verify(freeCashLimitChecker)
        .check(fund, new BigDecimal("80000"), new BigDecimal("5000"), BigDecimal.ZERO, fundLimit);
  }

  @Test
  void runChecksAsOfUsesProvidedDate() {
    service = createService();
    var asOfDate = LocalDate.of(2026, 3, 2);

    for (var fund : TulevaFund.values()) {
      when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, asOfDate))
          .thenReturn(Optional.empty());
    }

    var results = service.runChecksAsOf(asOfDate);

    assertThat(results).isEmpty();
    for (var fund : TulevaFund.values()) {
      verify(fundPositionRepository).findLatestNavDateByFundAndAsOfDate(fund, asOfDate);
    }
  }

  @Test
  void freeCashSubtractsUnsettledSentOrders() {
    service = createService();
    var checkDate = LocalDate.of(2026, 3, 13);
    var fund = TUK75;

    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, checkDate))
        .thenReturn(Optional.of(checkDate));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(checkDate, fund, SECURITY))
        .thenReturn(List.of());
    when(navReportPositionProvider.getCalculatedAum(fund, checkDate))
        .thenReturn(Optional.of(BigDecimal.ZERO));
    var cashPosition =
        FundPosition.builder().marketValue(new BigDecimal("590345.14")).fund(fund).build();
    var feePosition =
        FundPosition.builder().marketValue(new BigDecimal("-68851.65")).fund(fund).build();
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(checkDate, fund, CASH))
        .thenReturn(List.of(cashPosition));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(checkDate, fund, LIABILITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(checkDate, fund, FEE))
        .thenReturn(List.of(feePosition));
    when(positionLimitRepository.findLatestByFundAsOf(fund, checkDate)).thenReturn(List.of());
    when(providerLimitRepository.findLatestByFundAsOf(fund, checkDate)).thenReturn(List.of());
    var fundLimit =
        FundLimit.builder()
            .fund(fund)
            .reserveSoft(new BigDecimal("500000"))
            .reserveHard(new BigDecimal("200000"))
            .maxFreeCash(new BigDecimal("1000000"))
            .build();
    when(fundLimitRepository.findLatestByFundAsOf(fund, checkDate))
        .thenReturn(Optional.of(fundLimit));
    when(modelPortfolioAllocationRepository.findLatestByFund(fund)).thenReturn(List.of());
    when(positionLimitChecker.check(any(), any(), any(), any())).thenReturn(List.of());
    when(providerLimitChecker.check(any(), any(), any(), any(), any())).thenReturn(List.of());

    var pendingBuy =
        TransactionOrder.builder()
            .fund(fund)
            .orderAmount(new BigDecimal("794310.00"))
            .transactionType(TransactionType.BUY)
            .build();
    var pendingSell =
        TransactionOrder.builder()
            .fund(fund)
            .orderAmount(new BigDecimal("100000.00"))
            .transactionType(TransactionType.SELL)
            .build();
    when(transactionOrderRepository.findUnsettledOrdersAsOf(eq(fund), eq(checkDate), any()))
        .thenReturn(List.of(pendingBuy, pendingSell));

    service.runChecksAsOf(checkDate);

    // net pending outflow = 794,310 (BUY) - 100,000 (SELL) = 694,310
    verify(freeCashLimitChecker)
        .check(
            eq(fund),
            argThat(c -> c.compareTo(new BigDecimal("590345.14")) == 0),
            argThat(l -> l.compareTo(new BigDecimal("-68851.65")) == 0),
            argThat(p -> p.compareTo(new BigDecimal("694310.00")) == 0),
            eq(fundLimit));
  }

  @Test
  void limitsAreResolvedUsingCheckDateNotToday() {
    service = createService();
    var checkDate = LocalDate.of(2026, 3, 13);
    var fund = TUK75;

    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, checkDate))
        .thenReturn(Optional.of(checkDate));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(checkDate, fund, SECURITY))
        .thenReturn(List.of());
    when(navReportPositionProvider.getCalculatedAum(fund, checkDate))
        .thenReturn(Optional.of(BigDecimal.ZERO));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(checkDate, fund, CASH))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(checkDate, fund, LIABILITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(checkDate, fund, FEE))
        .thenReturn(List.of());
    when(modelPortfolioAllocationRepository.findLatestByFund(fund)).thenReturn(List.of());

    var oldPositionLimit =
        PositionLimit.builder()
            .isin("IE00BKPTWY98")
            .fund(fund)
            .effectiveDate(LocalDate.of(2025, 6, 30))
            .softLimitPercent(new BigDecimal("10.70"))
            .hardLimitPercent(new BigDecimal("11.50"))
            .build();
    when(positionLimitRepository.findLatestByFundAsOf(fund, checkDate))
        .thenReturn(List.of(oldPositionLimit));
    when(providerLimitRepository.findLatestByFundAsOf(fund, checkDate)).thenReturn(List.of());
    when(fundLimitRepository.findLatestByFundAsOf(fund, checkDate)).thenReturn(Optional.empty());
    when(positionLimitChecker.check(any(), any(), any(), any())).thenReturn(List.of());
    when(providerLimitChecker.check(any(), any(), any(), any(), any())).thenReturn(List.of());

    service.runChecksAsOf(checkDate);

    verify(positionLimitRepository).findLatestByFundAsOf(fund, checkDate);
    verify(providerLimitRepository).findLatestByFundAsOf(fund, checkDate);
    verify(fundLimitRepository).findLatestByFundAsOf(fund, checkDate);
    verify(positionLimitRepository, never()).findLatestByFund(any());
    verify(providerLimitRepository, never()).findLatestByFund(any());
    verify(fundLimitRepository, never()).findLatestByFund(any());
    verify(positionLimitChecker).check(eq(fund), anyList(), any(), eq(List.of(oldPositionLimit)));
  }

  @Test
  void backfillChecksProcessesMultipleDays() {
    service = createService();

    for (var fund : TulevaFund.values()) {
      when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(
              eq(fund), any(LocalDate.class)))
          .thenReturn(Optional.empty());
    }

    var results = service.backfillChecks(2);

    assertThat(results).isEmpty();
    var fundCount = TulevaFund.values().length;
    verify(fundPositionRepository, times(3 * fundCount))
        .findLatestNavDateByFundAndAsOfDate(any(), any());
  }

  @Test
  void usesNavReportPricesForPositionWeights() {
    service = createService();
    var today = LocalDate.of(2026, 3, 4);
    var fund = TUK75;

    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, today))
        .thenReturn(Optional.of(today));

    // FundPosition has custodian price (market_value=100,000)
    var position =
        FundPosition.builder()
            .accountId("IE001")
            .fund(fund)
            .marketValue(new BigDecimal("100000"))
            .build();
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(today, fund, SECURITY))
        .thenReturn(List.of(position));

    // Nav report has different (NAV calculation) price: 95,000
    when(navReportPositionProvider.getSecurityMarketValues(fund, today))
        .thenReturn(Map.of("IE001", new BigDecimal("95000")));

    when(navReportPositionProvider.getCalculatedAum(fund, today))
        .thenReturn(Optional.of(new BigDecimal("1000000")));

    when(fundPositionRepository.findByNavDateAndFundAndAccountType(today, fund, CASH))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(today, fund, LIABILITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(today, fund, FEE))
        .thenReturn(List.of());

    when(positionLimitRepository.findLatestByFundAsOf(fund, today)).thenReturn(List.of());
    when(providerLimitRepository.findLatestByFundAsOf(fund, today)).thenReturn(List.of());
    when(fundLimitRepository.findLatestByFundAsOf(fund, today)).thenReturn(Optional.empty());
    when(modelPortfolioAllocationRepository.findLatestByFund(fund)).thenReturn(List.of());
    when(positionLimitChecker.check(any(), any(), any(), any())).thenReturn(List.of());
    when(providerLimitChecker.check(any(), any(), any(), any(), any())).thenReturn(List.of());

    service.runChecksAsOf(today);

    // Position market_value should be overridden to 95,000 (NAV report), not 100,000 (custodian)
    verify(positionLimitChecker)
        .check(
            eq(fund),
            argThat(
                positions ->
                    positions.size() == 1
                        && positions.getFirst().getMarketValue().compareTo(new BigDecimal("95000"))
                            == 0),
            any(),
            anyList());
  }

  @Test
  void usesCalculatedAumFromNavReport() {
    service = createService();
    var today = LocalDate.of(2026, 3, 4);
    var fund = TUK75;

    when(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(fund, today))
        .thenReturn(Optional.of(today));

    var position =
        FundPosition.builder()
            .accountId("IE001")
            .fund(fund)
            .marketValue(new BigDecimal("100000"))
            .build();
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(today, fund, SECURITY))
        .thenReturn(List.of(position));

    // Calculated AUM from nav_report UNITS row = 1,000,000
    when(navReportPositionProvider.getCalculatedAum(fund, today))
        .thenReturn(Optional.of(new BigDecimal("1000000")));

    when(fundPositionRepository.findByNavDateAndFundAndAccountType(today, fund, CASH))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(today, fund, LIABILITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(today, fund, FEE))
        .thenReturn(List.of());

    when(positionLimitRepository.findLatestByFundAsOf(fund, today)).thenReturn(List.of());
    when(providerLimitRepository.findLatestByFundAsOf(fund, today)).thenReturn(List.of());
    when(fundLimitRepository.findLatestByFundAsOf(fund, today)).thenReturn(Optional.empty());
    when(modelPortfolioAllocationRepository.findLatestByFund(fund)).thenReturn(List.of());
    when(positionLimitChecker.check(any(), any(), any(), any())).thenReturn(List.of());
    when(providerLimitChecker.check(any(), any(), any(), any(), any())).thenReturn(List.of());

    service.runChecksAsOf(today);

    // totalNav should be 1,000,000 from nav_report calculated AUM
    verify(positionLimitChecker)
        .check(eq(fund), anyList(), eq(new BigDecimal("1000000")), anyList());
  }

  private LimitCheckService createService() {
    lenient()
        .when(navReportPositionProvider.getSecurityMarketValues(any(), any()))
        .thenReturn(Map.of());
    lenient()
        .when(navReportPositionProvider.getCalculatedAum(any(), any()))
        .thenReturn(Optional.empty());
    lenient()
        .when(transactionOrderRepository.findUnsettledOrdersAsOf(any(), any(), any()))
        .thenReturn(List.of());
    return new LimitCheckService(
        clock,
        fundPositionRepository,
        fundValueProvider,
        navReportPositionProvider,
        positionLimitRepository,
        providerLimitRepository,
        fundLimitRepository,
        modelPortfolioAllocationRepository,
        limitCheckEventRepository,
        positionLimitChecker,
        providerLimitChecker,
        reserveLimitChecker,
        freeCashLimitChecker,
        transactionOrderRepository);
  }
}
