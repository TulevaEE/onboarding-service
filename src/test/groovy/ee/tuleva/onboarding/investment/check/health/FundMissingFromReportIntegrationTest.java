package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.WARNING;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.RECEIVABLES;
import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.ExecutedPriceSource;
import ee.tuleva.onboarding.investment.transaction.OpenTradeSource;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@Import({
  HealthCheckService.class,
  FundInReportChecker.class,
  CompletenessChecker.class,
  HealthCheckNotifier.class
})
class FundMissingFromReportIntegrationTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 10, 30);

  @Autowired HealthCheckService healthCheckService;
  @Autowired HealthCheckNotifier notifier;

  @MockitoBean OperationsNotificationService notificationService;
  @MockitoBean ReceivablesChecker receivablesChecker;
  @MockitoBean FundPositionRepository fundPositionRepository;
  @MockitoBean ModelPortfolioAllocationRepository modelPortfolioAllocationRepository;
  @MockitoBean IsinMatchChecker isinMatchChecker;
  @MockitoBean OutstandingUnitsChecker outstandingUnitsChecker;
  @MockitoBean UnitReconciliationChecker unitReconciliationChecker;
  @MockitoBean NavUnitImpactChecker navUnitImpactChecker;
  @MockitoBean UnitReconciliationThresholdRepository unitReconciliationThresholdRepository;
  @MockitoBean AuthoritativeUnitsSource authoritativeUnitsSource;
  @MockitoBean QuantityChangeChecker quantityChangeChecker;
  @MockitoBean PayablesChecker payablesChecker;
  @MockitoBean NavFlowConsistencyChecker navFlowConsistencyChecker;
  @MockitoBean LiabilityRecognitionChecker liabilityRecognitionChecker;
  @MockitoBean InvestmentParameterRepository investmentParameterRepository;
  @MockitoBean ExecutedPriceSource executedPriceSource;
  @MockitoBean ExitMarkResolver exitMarkResolver;
  @MockitoBean OpenTradeSource openTradeSource;
  @MockitoBean UnsettledTradesChecker unsettledTradesChecker;
  @MockitoBean BankBalanceChecker bankBalanceChecker;

  @AfterEach
  void resetClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void aFundMissingFromAReSentReportIsReportedOnceAndClearsWhenAReportHasItAgain() {
    given(receivablesChecker.check(eq(TUV100), any(), any(), any(), any()))
        .willReturn(
            List.of(
                new HealthCheckFinding(
                    TUV100, RECEIVABLES, WARNING, "TUV100: a quantity fell with no receivable")));
    importReport("2026-10-31T06:30:00Z", complete(TUK75, TUK00, TUV100, TKF100));
    importReport("2026-10-31T06:35:00Z", complete(TUK75, TUK00, TUV100, TKF100));
    clearInvocations(notificationService);

    var missingReported =
        importReport(
            "2026-10-31T07:00:00Z",
            complete(TUK75, TUK00, TKF100),
            Map.of(TUV100, Instant.parse("2026-10-31T06:35:00Z")));

    assertThat(missingReported).isTrue();
    verify(notificationService)
        .sendMessage(
            and(
                and(
                    contains(
                        "Import warning: SEB 2026-10-30 — TUV100 positions written 2026-10-31"
                            + " 08:35 stay in use for NAV\n"),
                    contains("TUV100 not in the report (earlier positions in use)")),
                and(contains(missingLine(TUV100, "15:20")), not(contains("RECEIVABLES")))),
            eq(INVESTMENT));

    var repeatReported = importReport("2026-10-31T07:05:00Z", complete(TUK75, TUK00, TKF100));

    assertThat(repeatReported).isFalse();

    clearInvocations(notificationService);
    var backReported = importReport("2026-10-31T07:30:00Z", complete(TUK75, TUK00, TUV100, TKF100));

    assertThat(backReported).isTrue();
    verify(notificationService)
        .sendMessage(
            and(
                and(
                    contains("✅ Import cleared: SEB 2026-10-30\n"),
                    contains("TUV100 imported with warnings")),
                and(contains("\n\n[CLEARED] FUND_IN_REPORT TUV100"), not(contains("RECEIVABLES")))),
            eq(INVESTMENT));
  }

  @Test
  void aFundWithACompletenessWarningIsStillReportedMissingAndClearsWhenItIsBackWithThatWarning() {
    var withoutTuv100Cash = new ArrayList<>(complete(TUK75, TUK00, TKF100));
    withoutTuv100Cash.add(security(TUV100));
    importReport("2026-10-31T06:30:00Z", withoutTuv100Cash);
    importReport("2026-10-31T06:35:00Z", withoutTuv100Cash);
    clearInvocations(notificationService);

    var missingReported = importReport("2026-10-31T07:00:00Z", complete(TUK75, TUK00, TKF100));

    assertThat(missingReported).isTrue();
    verify(notificationService)
        .sendMessage(
            and(contains(missingLine(TUV100, "15:20")), not(contains("COMPLETENESS"))),
            eq(INVESTMENT));

    clearInvocations(notificationService);
    var backReported = importReport("2026-10-31T07:30:00Z", withoutTuv100Cash);

    assertThat(backReported).isTrue();
    verify(notificationService)
        .sendMessage(
            and(contains("\n\n[CLEARED] FUND_IN_REPORT TUV100"), not(contains("COMPLETENESS"))),
            eq(INVESTMENT));
  }

  @Test
  void aReportWithNoDataRowsAsksSebToResendItInOneLineForEveryStartedNavFund() {
    var reported = importReport("2026-10-31T07:00:00Z", List.of(), 0);

    assertThat(reported).isTrue();
    verify(notificationService)
        .sendMessage(
            and(
                and(
                    contains(
                        "Funds: TUK75 not in the report, TUK00 not in the report, TUV100 not in"
                            + " the report, TKF100 not in the report\n"),
                    contains(
                        "\n⚠️ [WARNING] FUND_IN_REPORT TUK75, TUK00, TUV100, TKF100: SEB's"
                            + " position report for navDate=2026-10-30 has no data rows. Ask SEB"
                            + " to resend it before the 11:00 NAV")),
                not(contains("TUV100: not in SEB's position report"))),
            eq(INVESTMENT));
  }

  @Test
  void aReportWhoseRowsCouldNotBeReadSaysSoInOneLineAndClearsOnceItsFundRowsCanBeRead() {
    var unreadReported = importReport("2026-10-31T07:00:00Z", List.of(), 412);

    assertThat(unreadReported).isTrue();
    verify(notificationService)
        .sendMessage(
            and(
                and(
                    contains(
                        "Funds: TUK75 not read from the report, TUK00 not read from the report,"
                            + " TUV100 not read from the report, TKF100 not read from the"
                            + " report\n"),
                    contains(
                        "\n⚠️ [WARNING] FUND_IN_REPORT TUK75, TUK00, TUV100, TKF100: none of the"
                            + " 412 rows in SEB's position report for navDate=2026-10-30 could be"
                            + " read as fund positions: check the file and the parser")),
                not(contains("Ask SEB"))),
            eq(INVESTMENT));

    clearInvocations(notificationService);
    var readReported = importReport("2026-10-31T07:30:00Z", complete(TUK75, TUK00, TUV100, TKF100));

    assertThat(readReported).isTrue();
    verify(notificationService)
        .sendMessage(
            and(
                and(
                    contains("✅ Import cleared: SEB 2026-10-30\n"),
                    and(
                        contains("[CLEARED] FUND_IN_REPORT TUK75"),
                        contains("[CLEARED] FUND_IN_REPORT TUK00"))),
                and(
                    contains("[CLEARED] FUND_IN_REPORT TUV100"),
                    contains("[CLEARED] FUND_IN_REPORT TKF100"))),
            eq(INVESTMENT));
  }

  private boolean importReport(String at, List<FundPosition> positions) {
    return importReport(at, positions, positions.size());
  }

  private boolean importReport(String at, List<FundPosition> positions, int reportRows) {
    return importReport(at, positions, reportRows, Map.of());
  }

  private boolean importReport(
      String at, List<FundPosition> positions, Map<TulevaFund, Instant> earlierImports) {
    return importReport(at, positions, positions.size(), earlierImports);
  }

  private boolean importReport(
      String at,
      List<FundPosition> positions,
      int reportRows,
      Map<TulevaFund, Instant> earlierImports) {
    ClockHolder.setClock(Clock.fixed(Instant.parse(at), UTC));
    var results = healthCheckService.check(NAV_DATE, positions, reportRows);
    return notifier.notify(SEB, NAV_DATE, results, earlierImports);
  }

  private static String missingLine(TulevaFund fund, String navTime) {
    return ("⚠️ [WARNING] FUND_IN_REPORT %s: %s: not in SEB's position report for"
            + " navDate=2026-10-30. Ask SEB to resend the report with %s before the %s NAV")
        .formatted(fund, fund, fund, navTime);
  }

  private List<FundPosition> complete(TulevaFund... funds) {
    return Arrays.stream(funds)
        .flatMap(fund -> List.of(security(fund), cash(fund)).stream())
        .toList();
  }

  private FundPosition security(TulevaFund fund) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(fund)
        .accountType(SECURITY)
        .accountName("iShares")
        .accountId("IE00BFG1TM61")
        .quantity(new BigDecimal("1000"))
        .build();
  }

  private FundPosition cash(TulevaFund fund) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .fund(fund)
        .accountType(CASH)
        .accountName("Cash account in SEB Pank")
        .marketValue(new BigDecimal("50000"))
        .build();
  }
}
