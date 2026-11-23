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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.investment.portfolio.ModelPortfolioAllocationRepository;
import ee.tuleva.onboarding.investment.position.FundPosition;
import ee.tuleva.onboarding.investment.position.FundPositionRepository;
import ee.tuleva.onboarding.investment.transaction.ExecutedPriceSource;
import ee.tuleva.onboarding.investment.transaction.OpenTradeSource;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.FundNavQueryService;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRepository;
import ee.tuleva.onboarding.savings.fund.nav.NavReportRow;
import ee.tuleva.onboarding.time.ClockConfig;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

@DataJpaTest
@Import({
  HealthCheckService.class,
  FundInReportChecker.class,
  CompletenessChecker.class,
  HealthCheckNotifier.class,
  HealthCheckAlertOutbox.class,
  HealthCheckAlertRepository.class,
  HealthCheckAlertPostingJob.class,
  FundNavQueryService.class,
  PublicHolidays.class,
  ClockConfig.class
})
class FundMissingFromReportIntegrationTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 10, 30);

  @Autowired HealthCheckService healthCheckService;
  @Autowired HealthCheckNotifier notifier;
  @Autowired HealthCheckAlertPostingJob postingJob;
  @Autowired NavReportRepository navReportRepository;
  @Autowired JdbcClient jdbcClient;

  @MockitoSpyBean HealthCheckAlertRepository alertRepository;
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
  void twoFundsMissingThenOnlyOneBackStillWarnsThatTheOtherKeepsItsEarlierPositions() {
    var writtenEarlier = Instant.parse("2026-10-31T06:35:00Z");
    importReport("2026-10-31T06:30:00Z", complete(TUK75, TUK00, TUV100, TKF100));
    importReport("2026-10-31T06:35:00Z", complete(TUK75, TUK00, TUV100, TKF100));
    importReport(
        "2026-10-31T07:00:00Z",
        complete(TUK75, TUK00),
        Map.of(TUV100, writtenEarlier, TKF100, writtenEarlier));
    clearInvocations(notificationService);

    var oneBackReported =
        importReport(
            "2026-10-31T07:30:00Z", complete(TUK75, TUK00, TKF100), Map.of(TUV100, writtenEarlier));

    assertThat(oneBackReported).isTrue();
    verify(notificationService)
        .sendMessage(
            and(
                and(
                    contains(
                        "Import warning: SEB 2026-10-30 — TUV100 positions written 2026-10-31"
                            + " 08:35 stay in use for NAV\n"),
                    contains("TUV100 not in the report (earlier positions in use)")),
                and(
                    contains("\n[CLEARED] FUND_IN_REPORT TKF100"),
                    not(contains("Import cleared")))),
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
  void anAlertIsSavedWithoutAPostAndTheNextRunPostsItWithTheNormalText() {
    importReport("2026-10-31T06:30:00Z", complete(TUK75, TUK00, TUV100, TKF100));

    var missingRaised = raiseAlerts("2026-10-31T07:00:00Z", complete(TUK75, TUK00, TKF100));

    assertThat(missingRaised).isTrue();
    then(notificationService).shouldHaveNoInteractions();
    assertThat(alertRows()).containsExactly("NOT_POSTED, attempts 0");

    runJobAt("2026-10-31T07:00:30Z");
    runJobAt("2026-10-31T07:01:30Z");

    verify(notificationService)
        .sendMessage(
            and(
                startsWith("Import warning: SEB 2026-10-30 — TUV100 not imported\nFunds: "),
                contains(missingLine(TUV100, "15:20"))),
            eq(INVESTMENT));
    verifyNoMoreInteractions(notificationService);
    assertThat(alertRows()).containsExactly("POSTED, attempts 1");
  }

  @Test
  void anAlertSavedBeforeTheServiceStoppedIsPostedWhenTheJobRunsAgainSayingWhenItWasFound() {
    importReport("2026-10-31T06:30:00Z", complete(TUK75, TUK00, TUV100, TKF100));
    raiseAlerts("2026-10-31T07:00:00Z", complete(TUK75, TUK00, TKF100));

    runJobAt("2026-10-31T07:20:30Z");

    verify(notificationService)
        .sendMessage(
            and(
                startsWith(
                    "Import warning: SEB 2026-10-30 — TUV100 not imported"
                        + " (found 2026-10-31 09:00, posted late)\nFunds: "),
                contains(missingLine(TUV100, "15:20"))),
            eq(INVESTMENT));
    assertThat(alertRows()).containsExactly("POSTED, attempts 1");
  }

  @Test
  void anAlertWhosePostAStopCutShortIsPostedAgainOnlyAfterItsGap() {
    importReport("2026-10-31T06:30:00Z", complete(TUK75, TUK00, TUV100, TKF100));
    raiseAlerts("2026-10-31T07:00:00Z", complete(TUK75, TUK00, TKF100));
    jdbcClient
        .sql("UPDATE investment_health_check_alert SET attempts = 1, last_attempt_at = :at")
        .param("at", Timestamp.from(Instant.parse("2026-10-31T07:00:30Z")))
        .update();

    runJobAt("2026-10-31T07:01:00Z");

    then(notificationService).shouldHaveNoInteractions();

    runJobAt("2026-10-31T07:01:30Z");

    verify(notificationService)
        .sendMessage(
            startsWith("Import warning: SEB 2026-10-30 — TUV100 not imported\n"), eq(INVESTMENT));
    assertThat(alertRows()).containsExactly("POSTED, attempts 2");
  }

  @Test
  void alertsArePostedOldestFirstAndANewerOneNeverOvertakesAnOlderOneWaitingForItsGap() {
    importReport("2026-10-31T06:30:00Z", complete(TUK75, TUK00, TUV100, TKF100));
    raiseAlerts("2026-10-31T07:00:00Z", complete(TUK75, TUK00, TKF100));
    slackIsDown();
    runJobAt("2026-10-31T07:00:30Z");
    slackIsBack();
    raiseAlerts("2026-10-31T07:01:00Z", complete(TUK75, TUK00, TUV100, TKF100));

    runJobAt("2026-10-31T07:01:05Z");

    then(notificationService).shouldHaveNoInteractions();

    runJobAt("2026-10-31T07:01:30Z");

    var inOrder = inOrder(notificationService);
    inOrder
        .verify(notificationService)
        .sendMessage(
            startsWith("Import warning: SEB 2026-10-30 — TUV100 not imported\n"), eq(INVESTMENT));
    inOrder
        .verify(notificationService)
        .sendMessage(
            and(
                startsWith("✅ Import cleared: SEB 2026-10-30\n"),
                contains("\n[CLEARED] FUND_IN_REPORT TUV100")),
            eq(INVESTMENT));
    verifyNoMoreInteractions(notificationService);
    assertThat(alertRows()).containsExactly("POSTED, attempts 2", "POSTED, attempts 1");
  }

  @Test
  void anAlertSlackProvablyNeverReceivedGetsItsAttemptBackAndIsPostedByTheNextRun() {
    importReport("2026-10-31T06:30:00Z", complete(TUK75, TUK00, TUV100, TKF100));
    raiseAlerts("2026-10-31T07:00:00Z", complete(TUK75, TUK00, TKF100));
    willThrow(
            new ResourceAccessException(
                "I/O error on POST request: Connection refused",
                new ConnectException("Connection refused")))
        .given(notificationService)
        .sendMessage(anyString(), eq(INVESTMENT));

    runJobAt("2026-10-31T07:00:30Z");

    assertThat(alertRows()).containsExactly("NOT_POSTED, attempts 0");

    slackIsBack();
    runJobAt("2026-10-31T07:01:30Z");

    verify(notificationService)
        .sendMessage(
            startsWith("Import warning: SEB 2026-10-30 — TUV100 not imported\n"), eq(INVESTMENT));
    assertThat(alertRows()).containsExactly("POSTED, attempts 1");
  }

  @Test
  void anAlertSlackMayHaveReceivedWaitsOneThenTwoThenFourMinutesBeforeItsNextAttempts() {
    importReport("2026-10-31T06:30:00Z", complete(TUK75, TUK00, TUV100, TKF100));
    raiseAlerts("2026-10-31T07:00:00Z", complete(TUK75, TUK00, TKF100));
    willThrow(
            new ResourceAccessException(
                "I/O error on POST request: Read timed out",
                new SocketTimeoutException("Read timed out")))
        .given(notificationService)
        .sendMessage(anyString(), eq(INVESTMENT));

    var attemptsAfterEachRun =
        IntStream.rangeClosed(0, 7)
            .mapToObj(
                minute -> {
                  runJobAt("2026-10-31T07:0%d:30Z".formatted(minute));
                  return mockingDetails(notificationService).getInvocations().size();
                })
            .toList();

    assertThat(attemptsAfterEachRun).containsExactly(1, 2, 2, 3, 3, 3, 3, 4);
    assertThat(alertRows()).containsExactly("NOT_POSTED, attempts 4");
  }

  @Test
  void aPostWhoseRecordingFailsIsNotPostedAgainBeforeItsGap() {
    importReport("2026-10-31T06:30:00Z", complete(TUK75, TUK00, TUV100, TKF100));
    raiseAlerts("2026-10-31T07:00:00Z", complete(TUK75, TUK00, TKF100));
    willThrow(new DataAccessResourceFailureException("database down"))
        .willCallRealMethod()
        .given(alertRepository)
        .markPosted(anyLong(), any());

    runJobAt("2026-10-31T07:00:30Z");
    runJobAt("2026-10-31T07:01:00Z");

    verify(notificationService).sendMessage(anyString(), eq(INVESTMENT));

    runJobAt("2026-10-31T07:01:30Z");

    verify(notificationService, times(2))
        .sendMessage(
            startsWith("Import warning: SEB 2026-10-30 — TUV100 not imported\n"), eq(INVESTMENT));
    assertThat(alertRows()).containsExactly("POSTED, attempts 2");
  }

  @Test
  void anAlertNotPostedWithinADayOfItsStateBeingFoundIsGivenUpAndNeverPosted() {
    importReport("2026-10-31T06:30:00Z", complete(TUK75, TUK00, TUV100, TKF100));
    raiseAlerts("2026-10-31T07:00:00Z", complete(TUK75, TUK00, TKF100));
    slackIsDown();
    runJobAt("2026-10-31T07:00:30Z");
    slackIsBack();

    runJobAt("2026-11-01T07:00:30Z");
    runJobAt("2026-11-01T07:01:30Z");

    then(notificationService).shouldHaveNoInteractions();
    assertThat(alertRows()).containsExactly("GIVEN_UP, attempts 1");
  }

  @Test
  void aFundMissingFromAReSentReportAfterItsNavIsPublishedIsNotGivenANavDeadline() {
    navReportRepository.save(
        NavReportRow.builder()
            .navDate(NAV_DATE)
            .fundCode("TUV100")
            .accountType("NAV")
            .accountName("Net Asset Value")
            .calculationId(UUID.randomUUID())
            .publishedAt(Instant.parse("2026-11-02T13:21:00Z"))
            .build());
    importReport("2026-11-02T06:30:00Z", complete(TUK75, TUK00, TUV100, TKF100));

    var reported = importReport("2026-11-02T15:00:00Z", complete(TUK75, TUK00, TKF100));

    assertThat(reported).isTrue();
    verify(notificationService)
        .sendMessage(
            and(
                contains(
                    "\n⚠️ [WARNING] FUND_IN_REPORT TUV100: TUV100: not in SEB's position report"
                        + " for navDate=2026-10-30. Ask SEB to resend the report with TUV100"),
                not(contains("before the"))),
            eq(INVESTMENT));
  }

  @Test
  void aReportWithNoPositionRowsAsksSebToResendItInOneLinePerNavDeadline() {
    var reported = importReport("2026-10-31T07:00:00Z", List.of(), 0);

    assertThat(reported).isTrue();
    verify(notificationService)
        .sendMessage(
            and(
                and(
                    contains(
                        "Funds: TUK75 not in the report, TUK00 not in the report, TUV100 not in"
                            + " the report, TKF100 not in the report\n"),
                    and(
                        contains(
                            "\n⚠️ [WARNING] FUND_IN_REPORT TUK75, TUK00: SEB's position report"
                                + " for navDate=2026-10-30 has no position rows. Ask SEB to resend"
                                + " it before the 11:00 NAV"),
                        contains(
                            "\n⚠️ [WARNING] FUND_IN_REPORT TUV100, TKF100: SEB's position"
                                + " report for navDate=2026-10-30 has no position rows. Ask SEB to"
                                + " resend it before the 15:20 NAV"))),
                not(contains("TUV100: not in SEB's position report"))),
            eq(INVESTMENT));
  }

  @Test
  void aReportWhosePositionRowsNameNoFundIsPutOnTheFileAndTheParserAndClearsOnceItsFundsAreRead() {
    var reported = importReport("2026-10-31T07:00:00Z", List.of(), 412);

    assertThat(reported).isTrue();
    verify(notificationService)
        .sendMessage(
            and(
                and(
                    contains("TUK75 not read from the report"),
                    and(
                        contains(
                            "\n⚠️ [WARNING] FUND_IN_REPORT TUK75, TUK00: "
                                + NO_ROW_NAMES_A_FUND
                                + " before the 11:00 NAV"),
                        contains(
                            "\n⚠️ [WARNING] FUND_IN_REPORT TUV100, TKF100: "
                                + NO_ROW_NAMES_A_FUND
                                + " before the 15:20 NAV"))),
                not(contains("Ask SEB"))),
            eq(INVESTMENT));

    clearInvocations(notificationService);
    var readReported = importReport("2026-10-31T07:30:00Z", complete(TUK75, TUK00, TUV100, TKF100));

    assertThat(readReported).isTrue();
    verify(notificationService)
        .sendMessage(
            and(
                contains("✅ Import cleared: SEB 2026-10-30\n"),
                contains("\n[CLEARED] FUND_IN_REPORT TUK75, TUK00, TUV100, TKF100")),
            eq(INVESTMENT));
  }

  @Test
  void aReSentReportWhoseRowsNameNoFundAfterAnEmptyOneIsPostedOnceAsAParserProblem() {
    importReport("2026-10-31T07:00:00Z", List.of(), 0);
    clearInvocations(notificationService);

    var movedToParser = importReport("2026-10-31T07:30:00Z", List.of(), 412);

    assertThat(movedToParser).isTrue();
    verify(notificationService)
        .sendMessage(
            contains(
                "\n⚠️ [WARNING] FUND_IN_REPORT TUK75, TUK00: "
                    + NO_ROW_NAMES_A_FUND
                    + " before the 11:00 NAV"),
            eq(INVESTMENT));

    var sameAgain = importReport("2026-10-31T07:35:00Z", List.of(), 412);

    assertThat(sameAgain).isFalse();
  }

  private static final String NO_ROW_NAMES_A_FUND =
      "none of the 412 position rows in SEB's position report for navDate=2026-10-30 names a"
          + " fund: check the file and the parser";

  private boolean importReport(String at, List<FundPosition> positions) {
    return importReport(at, positions, positions.size(), Map.of());
  }

  private boolean importReport(String at, List<FundPosition> positions, int positionRows) {
    return importReport(at, positions, positionRows, Map.of());
  }

  private boolean importReport(
      String at, List<FundPosition> positions, Map<TulevaFund, Instant> earlierImports) {
    return importReport(at, positions, positions.size(), earlierImports);
  }

  private boolean importReport(
      String at,
      List<FundPosition> positions,
      int positionRows,
      Map<TulevaFund, Instant> earlierImports) {
    var raised = raiseAlerts(at, positions, positionRows, earlierImports);
    postingJob.postAlertsNotYetPosted();
    return raised;
  }

  private boolean raiseAlerts(String at, List<FundPosition> positions) {
    return raiseAlerts(at, positions, positions.size(), Map.of());
  }

  private boolean raiseAlerts(
      String at,
      List<FundPosition> positions,
      int positionRows,
      Map<TulevaFund, Instant> earlierImports) {
    ClockHolder.setClock(Clock.fixed(Instant.parse(at), UTC));
    var results = healthCheckService.check(NAV_DATE, positions, positionRows);
    return notifier.notify(SEB, NAV_DATE, results, earlierImports);
  }

  private void slackIsDown() {
    willThrow(new RestClientException("Slack is down"))
        .given(notificationService)
        .sendMessage(anyString(), eq(INVESTMENT));
  }

  private void slackIsBack() {
    willDoNothing().given(notificationService).sendMessage(anyString(), eq(INVESTMENT));
    clearInvocations(notificationService);
  }

  private void runJobAt(String at) {
    ClockHolder.setClock(Clock.fixed(Instant.parse(at), UTC));
    postingJob.postAlertsNotYetPosted();
  }

  private List<String> alertRows() {
    return jdbcClient
        .sql("SELECT status, attempts FROM investment_health_check_alert ORDER BY id")
        .query((rs, rowNum) -> rs.getString("status") + ", attempts " + rs.getInt("attempts"))
        .list();
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
