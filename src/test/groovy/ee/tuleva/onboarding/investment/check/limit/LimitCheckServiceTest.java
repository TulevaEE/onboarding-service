package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.OK;
import static ee.tuleva.onboarding.investment.check.limit.CheckType.*;
import static ee.tuleva.onboarding.investment.portfolio.Provider.ISHARES;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.calculation.InvestmentPositionCalculation;
import ee.tuleva.onboarding.investment.calculation.PositionCalculationRepository;
import ee.tuleva.onboarding.investment.portfolio.*;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LimitCheckServiceTest {

  @Mock PositionCalculationRepository positionCalculationRepository;
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

  Clock clock = Clock.fixed(Instant.parse("2026-03-04T16:00:00Z"), ZoneId.of("Europe/Tallinn"));

  @InjectMocks LimitCheckService service;

  @Test
  void delegatesToAllThreeCheckers() {
    service = createService();
    var today = LocalDate.of(2026, 3, 4);
    var fund = TUK75;
    var position =
        InvestmentPositionCalculation.builder()
            .isin("IE001")
            .fund(fund)
            .calculatedMarketValue(new BigDecimal("100000"))
            .build();
    var nonSecurityNav = new BigDecimal("100000");
    var securitiesNav = new BigDecimal("900000");
    var totalNav = new BigDecimal("1000000");

    when(positionCalculationRepository.getLatestDateUpTo(fund, today))
        .thenReturn(Optional.of(today));
    when(positionCalculationRepository.findByFundAndDate(fund, today))
        .thenReturn(List.of(position));
    when(fundPositionRepository.sumMarketValueByFundAndAccountTypes(
            fund, today, List.of(CASH, RECEIVABLES, LIABILITY)))
        .thenReturn(nonSecurityNav);
    when(positionCalculationRepository.getTotalMarketValue(fund, today))
        .thenReturn(Optional.of(securitiesNav));

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
    when(positionLimitRepository.findLatestByFund(fund)).thenReturn(List.of(positionLimit));

    var providerLimit =
        ProviderLimit.builder()
            .provider(ISHARES)
            .fund(fund)
            .softLimitPercent(new BigDecimal("30"))
            .hardLimitPercent(new BigDecimal("40"))
            .build();
    when(providerLimitRepository.findLatestByFund(fund)).thenReturn(List.of(providerLimit));

    var fundLimit =
        FundLimit.builder()
            .fund(fund)
            .reserveSoft(new BigDecimal("50000"))
            .reserveHard(new BigDecimal("30000"))
            .build();
    when(fundLimitRepository.findLatestByFund(fund)).thenReturn(Optional.of(fundLimit));

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
      when(positionCalculationRepository.getLatestDateUpTo(fund, today))
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

    when(positionCalculationRepository.getLatestDateUpTo(fund, today))
        .thenReturn(Optional.of(today));
    when(positionCalculationRepository.findByFundAndDate(fund, today)).thenReturn(List.of());
    when(fundPositionRepository.sumMarketValueByFundAndAccountTypes(
            fund, today, List.of(CASH, RECEIVABLES, LIABILITY)))
        .thenReturn(BigDecimal.ZERO);
    when(positionCalculationRepository.getTotalMarketValue(fund, today))
        .thenReturn(Optional.empty());

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

    when(positionLimitRepository.findLatestByFund(fund)).thenReturn(List.of());
    when(providerLimitRepository.findLatestByFund(fund)).thenReturn(List.of());
    var fundLimit =
        FundLimit.builder()
            .fund(fund)
            .reserveSoft(new BigDecimal("50000"))
            .reserveHard(new BigDecimal("30000"))
            .build();
    when(fundLimitRepository.findLatestByFund(fund)).thenReturn(Optional.of(fundLimit));
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
      when(positionCalculationRepository.getLatestDateUpTo(fund, asOfDate))
          .thenReturn(Optional.empty());
    }

    var results = service.runChecksAsOf(asOfDate);

    assertThat(results).isEmpty();
    for (var fund : TulevaFund.values()) {
      verify(positionCalculationRepository).getLatestDateUpTo(fund, asOfDate);
    }
  }

  @Test
  void backfillChecksProcessesMultipleDays() {
    service = createService();

    for (var fund : TulevaFund.values()) {
      when(positionCalculationRepository.getLatestDateUpTo(eq(fund), any(LocalDate.class)))
          .thenReturn(Optional.empty());
    }

    var results = service.backfillChecks(2);

    assertThat(results).isEmpty();
    var fundCount = TulevaFund.values().length;
    verify(positionCalculationRepository, times(3 * fundCount)).getLatestDateUpTo(any(), any());
  }

  private LimitCheckService createService() {
    return new LimitCheckService(
        clock,
        positionCalculationRepository,
        fundPositionRepository,
        positionLimitRepository,
        providerLimitRepository,
        fundLimitRepository,
        modelPortfolioAllocationRepository,
        limitCheckEventRepository,
        positionLimitChecker,
        providerLimitChecker,
        reserveLimitChecker,
        freeCashLimitChecker);
  }
}
