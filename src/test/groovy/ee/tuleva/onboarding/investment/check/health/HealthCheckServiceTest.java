package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.fund.TulevaFund.*;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.*;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.COMPLETENESS;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocation;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthCheckServiceTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 4, 15);

  @Mock FundPositionRepository fundPositionRepository;
  @Mock ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  @Mock HealthCheckEventRepository healthCheckEventRepository;
  @Mock CompletenessChecker completenessChecker;
  @Mock IsinMatchChecker isinMatchChecker;
  @Mock OutstandingUnitsChecker outstandingUnitsChecker;
  @Mock UnitReconciliationChecker unitReconciliationChecker;
  @Mock UnitReconciliationThresholdRepository unitReconciliationThresholdRepository;
  @Mock AuthoritativeUnitsSource authoritativeUnitsSource;
  @Mock ReceivablesChecker receivablesChecker;
  @Mock PayablesChecker payablesChecker;

  @InjectMocks HealthCheckService healthCheckService;

  @Test
  void delegatesToCheckersWithPositionsGroupedByFund() {
    var positions =
        List.of(
            securityPosition(TUK75, "IE001", new BigDecimal("1000")),
            cashPosition(TUK75, new BigDecimal("50000")));

    var allocations = List.of(ModelPortfolioAllocation.builder().fund(TUK75).isin("IE001").build());
    given(modelPortfolioAllocationRepository.findLatestByFund(TUK75)).willReturn(allocations);
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, NAV_DATE.minusDays(1)))
        .willReturn(Optional.empty());

    given(completenessChecker.check(eq(TUK75), eq(NAV_DATE), any())).willReturn(List.of());
    given(isinMatchChecker.check(eq(TUK75), any(), eq(allocations))).willReturn(List.of());
    given(outstandingUnitsChecker.check(eq(TUK75), eq(NAV_DATE), any())).willReturn(List.of());
    given(receivablesChecker.check(eq(TUK75), any(), any(), any(), any())).willReturn(List.of());
    given(payablesChecker.check(eq(TUK75), any(), any(), any(), any())).willReturn(List.of());

    var results = healthCheckService.check(positions);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().fund()).isEqualTo(TUK75);
    assertThat(results.getFirst().checkDate()).isEqualTo(NAV_DATE);
    verify(completenessChecker).check(eq(TUK75), eq(NAV_DATE), eq(positions));
  }

  @Test
  void checksMultipleFundsSeparately() {
    var positions =
        List.of(
            securityPosition(TUK75, "IE001", new BigDecimal("1000")),
            securityPosition(TUK00, "LU001", new BigDecimal("500")));

    given(modelPortfolioAllocationRepository.findLatestByFund(any())).willReturn(List.of());
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(any(), any()))
        .willReturn(Optional.empty());

    given(completenessChecker.check(any(), any(), any())).willReturn(List.of());
    given(isinMatchChecker.check(any(), any(), any())).willReturn(List.of());
    given(outstandingUnitsChecker.check(any(), any(), any())).willReturn(List.of());
    given(receivablesChecker.check(any(), any(), any(), any(), any())).willReturn(List.of());
    given(payablesChecker.check(any(), any(), any(), any(), any())).willReturn(List.of());

    var results = healthCheckService.check(positions);

    assertThat(results).hasSize(2);
    assertThat(results).extracting(HealthCheckResult::fund).containsExactlyInAnyOrder(TUK75, TUK00);
  }

  @Test
  void savesEventsPerCheckType() {
    var positions = List.of(securityPosition(TUK75, "IE001", new BigDecimal("1000")));

    given(modelPortfolioAllocationRepository.findLatestByFund(TUK75)).willReturn(List.of());
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, NAV_DATE.minusDays(1)))
        .willReturn(Optional.empty());

    var finding = new HealthCheckFinding(TUK75, COMPLETENESS, WARNING, "test");
    given(completenessChecker.check(eq(TUK75), eq(NAV_DATE), any())).willReturn(List.of(finding));
    given(isinMatchChecker.check(any(), any(), any())).willReturn(List.of());
    given(outstandingUnitsChecker.check(any(), any(), any())).willReturn(List.of());
    given(receivablesChecker.check(any(), any(), any(), any(), any())).willReturn(List.of());
    given(payablesChecker.check(any(), any(), any(), any(), any())).willReturn(List.of());

    healthCheckService.check(positions);

    verify(healthCheckEventRepository, times(HealthCheckType.values().length))
        .save(any(HealthCheckEvent.class));
  }

  @Test
  void persistsSeverityDerivedFromFindings() {
    var positions = List.of(securityPosition(TUK75, "IE001", new BigDecimal("1000")));

    given(modelPortfolioAllocationRepository.findLatestByFund(TUK75)).willReturn(List.of());
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, NAV_DATE.minusDays(1)))
        .willReturn(Optional.empty());

    var failFinding = new HealthCheckFinding(TUK75, COMPLETENESS, FAIL, "missing CASH");
    given(completenessChecker.check(eq(TUK75), eq(NAV_DATE), any()))
        .willReturn(List.of(failFinding));
    given(isinMatchChecker.check(any(), any(), any())).willReturn(List.of());
    given(outstandingUnitsChecker.check(any(), any(), any())).willReturn(List.of());
    given(receivablesChecker.check(any(), any(), any(), any(), any())).willReturn(List.of());
    given(payablesChecker.check(any(), any(), any(), any(), any())).willReturn(List.of());

    healthCheckService.check(positions);

    verify(healthCheckEventRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                e ->
                    e.getCheckType() == COMPLETENESS
                        && e.getSeverity() == FAIL
                        && e.isIssuesFound()));
    verify(healthCheckEventRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                e ->
                    e.getCheckType() == HealthCheckType.ISIN_MATCH
                        && e.getSeverity() == PASS
                        && !e.isIssuesFound()));
  }

  @Test
  void looksPreviousDaySecuritiesFromDatabase() {
    var previousDate = NAV_DATE.minusDays(1);
    var positions = List.of(securityPosition(TUK75, "IE001", new BigDecimal("900")));
    var previousSecurities = List.of(securityPosition(TUK75, "IE001", new BigDecimal("1000")));

    given(modelPortfolioAllocationRepository.findLatestByFund(TUK75)).willReturn(List.of());
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, NAV_DATE.minusDays(1)))
        .willReturn(Optional.of(previousDate));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(previousDate, TUK75, SECURITY))
        .willReturn(previousSecurities);

    given(completenessChecker.check(any(), any(), any())).willReturn(List.of());
    given(isinMatchChecker.check(any(), any(), any())).willReturn(List.of());
    given(outstandingUnitsChecker.check(any(), any(), any())).willReturn(List.of());
    given(receivablesChecker.check(eq(TUK75), any(), eq(previousSecurities), any(), any()))
        .willReturn(List.of());
    given(payablesChecker.check(eq(TUK75), any(), eq(previousSecurities), any(), any()))
        .willReturn(List.of());

    healthCheckService.check(positions);

    verify(receivablesChecker).check(eq(TUK75), any(), eq(previousSecurities), any(), any());
    verify(payablesChecker).check(eq(TUK75), any(), eq(previousSecurities), any(), any());
  }

  @Test
  void loadsPreviousDayLiabilitiesAndReceivablesFromDatabase() {
    var previousDate = NAV_DATE.minusDays(1);
    var positions = List.of(securityPosition(TUK75, "IE001", new BigDecimal("1100")));
    var previousLiabilities = List.of(liabilityPosition(TUK75, new BigDecimal("-1780760.00")));
    var previousReceivables = List.of(receivablesPosition(TUK75, new BigDecimal("831.77")));

    given(modelPortfolioAllocationRepository.findLatestByFund(TUK75)).willReturn(List.of());
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, NAV_DATE.minusDays(1)))
        .willReturn(Optional.of(previousDate));
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(previousDate, TUK75, SECURITY))
        .willReturn(List.of());
    given(fundPositionRepository.findByNavDateAndFundAndAccountType(previousDate, TUK75, LIABILITY))
        .willReturn(previousLiabilities);
    given(
            fundPositionRepository.findByNavDateAndFundAndAccountType(
                previousDate, TUK75, RECEIVABLES))
        .willReturn(previousReceivables);

    given(completenessChecker.check(any(), any(), any())).willReturn(List.of());
    given(isinMatchChecker.check(any(), any(), any())).willReturn(List.of());
    given(outstandingUnitsChecker.check(any(), any(), any())).willReturn(List.of());
    given(receivablesChecker.check(any(), any(), any(), any(), eq(previousReceivables)))
        .willReturn(List.of());
    given(payablesChecker.check(any(), any(), any(), any(), eq(previousLiabilities)))
        .willReturn(List.of());

    healthCheckService.check(positions);

    verify(payablesChecker).check(eq(TUK75), any(), any(), any(), eq(previousLiabilities));
    verify(receivablesChecker).check(eq(TUK75), any(), any(), any(), eq(previousReceivables));
  }

  @Test
  void invokesUnitReconciliationCheckerWithLoadedThresholdAndAuthoritativeUnits() {
    var positions = List.of(unitsPosition(TUK75, new BigDecimal("1000000")));
    var threshold =
        UnitReconciliationThreshold.builder()
            .fundCode(TUK75)
            .warningUnits(new BigDecimal("0"))
            .failUnits(null)
            .build();
    var authoritative = new BigDecimal("1000005");

    given(modelPortfolioAllocationRepository.findLatestByFund(TUK75)).willReturn(List.of());
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, NAV_DATE.minusDays(1)))
        .willReturn(Optional.empty());
    given(unitReconciliationThresholdRepository.findByFundCode(TUK75))
        .willReturn(Optional.of(threshold));
    given(authoritativeUnitsSource.resolve(TUK75)).willReturn(Optional.of(authoritative));

    healthCheckService.check(positions);

    verify(unitReconciliationChecker)
        .check(eq(TUK75), eq(NAV_DATE), eq(positions), eq(authoritative), eq(threshold));
  }

  @Test
  void passesNullsToUnitReconciliationCheckerWhenThresholdOrAuthoritativeAbsent() {
    var positions = List.of(unitsPosition(TUK75, new BigDecimal("1000000")));

    given(modelPortfolioAllocationRepository.findLatestByFund(TUK75)).willReturn(List.of());
    given(fundPositionRepository.findLatestNavDateByFundAndAsOfDate(TUK75, NAV_DATE.minusDays(1)))
        .willReturn(Optional.empty());
    given(unitReconciliationThresholdRepository.findByFundCode(TUK75)).willReturn(Optional.empty());
    given(authoritativeUnitsSource.resolve(TUK75)).willReturn(Optional.empty());

    healthCheckService.check(positions);

    verify(unitReconciliationChecker)
        .check(eq(TUK75), eq(NAV_DATE), eq(positions), eq(null), eq(null));
  }

  private FundPosition securityPosition(TulevaFund fund, String isin, BigDecimal quantity) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(fund)
        .accountType(SECURITY)
        .accountName("iShares")
        .accountId(isin)
        .quantity(quantity)
        .build();
  }

  private FundPosition cashPosition(TulevaFund fund, BigDecimal marketValue) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(fund)
        .accountType(CASH)
        .accountName("Cash account in SEB Pank")
        .marketValue(marketValue)
        .build();
  }

  private FundPosition liabilityPosition(TulevaFund fund, BigDecimal marketValue) {
    return FundPosition.builder()
        .navDate(NAV_DATE.minusDays(1))
        .fund(fund)
        .accountType(LIABILITY)
        .accountName("Total payables of unsettled transactions")
        .marketValue(marketValue)
        .build();
  }

  private FundPosition receivablesPosition(TulevaFund fund, BigDecimal marketValue) {
    return FundPosition.builder()
        .navDate(NAV_DATE.minusDays(1))
        .fund(fund)
        .accountType(RECEIVABLES)
        .accountName("Receivables of outstanding units")
        .marketValue(marketValue)
        .build();
  }

  private FundPosition unitsPosition(TulevaFund fund, BigDecimal quantity) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(fund)
        .accountType(ee.tuleva.onboarding.investment.position.AccountType.UNITS)
        .accountName("Total outstanding units")
        .quantity(quantity)
        .build();
  }
}
