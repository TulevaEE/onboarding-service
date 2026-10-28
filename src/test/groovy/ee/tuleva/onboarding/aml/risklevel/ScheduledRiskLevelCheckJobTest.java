package ee.tuleva.onboarding.aml.risklevel;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import ee.tuleva.onboarding.aml.notification.AmlRiskLevelJobFailedEvent;
import ee.tuleva.onboarding.aml.notification.AmlRiskLevelJobFellBackEvent;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class ScheduledRiskLevelCheckJobTest {

  private static final double MONTHLY_MEDIUM_RISK_TARGET_PROBABILITY = 0.025;
  private static final double DAYS_IN_MONTH_ASSUMPTION_FOR_DAILY_RUN = 30.0;
  private static final double EXPECTED_PROBABILITY =
      MONTHLY_MEDIUM_RISK_TARGET_PROBABILITY / DAYS_IN_MONTH_ASSUMPTION_FOR_DAILY_RUN;

  private static final AssessedRiskLevels THIRD_PILLAR_RUN =
      new AssessedRiskLevels(Instant.parse("2026-10-28T02:45:00Z"), List.of());
  private static final AssessedRiskLevels TKF_RUN =
      new AssessedRiskLevels(Instant.parse("2026-10-28T02:15:00Z"), List.of());

  private final RiskLevelService riskLevelService = mock(RiskLevelService.class);
  private final AmlRiskReader amlRiskReader = mock(AmlRiskReader.class);
  private final TkfRiskReader tkfRiskReader = mock(TkfRiskReader.class);
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

  @Test
  void runsTheViewsFlowAtOneAsBefore() throws NoSuchMethodException {
    Scheduled scheduled =
        ScheduledRiskLevelCheckJob.class.getMethod("run").getAnnotation(Scheduled.class);

    assertThat(scheduled.cron()).isEqualTo("0 0 1 * * ?");
    assertThat(scheduled.zone()).isEqualTo("Europe/Tallinn");
  }

  @Test
  void runsTheAmlServiceFlowAfterTheNightlyAmlServiceBatchRunsAndOverrideImport()
      throws NoSuchMethodException {
    Scheduled scheduled =
        ScheduledRiskLevelCheckJob.class
            .getMethod("runFromAmlService")
            .getAnnotation(Scheduled.class);

    assertThat(scheduled.cron()).isEqualTo("0 30 5 * * ?");
    assertThat(scheduled.zone()).isEqualTo("Europe/Tallinn");
  }

  @Test
  void eachRunHasItsOwnLockThatExpiresWellBeforeItsNextRun() throws NoSuchMethodException {
    SchedulerLock atOne =
        ScheduledRiskLevelCheckJob.class.getMethod("run").getAnnotation(SchedulerLock.class);
    SchedulerLock atHalfPastFive =
        ScheduledRiskLevelCheckJob.class
            .getMethod("runFromAmlService")
            .getAnnotation(SchedulerLock.class);

    assertThat(atOne)
        .extracting(
            SchedulerLock::name, SchedulerLock::lockAtMostFor, SchedulerLock::lockAtLeastFor)
        .containsExactly("ScheduledRiskLevelCheckJob_run", "1h", "30m");
    assertThat(atHalfPastFive)
        .extracting(
            SchedulerLock::name, SchedulerLock::lockAtMostFor, SchedulerLock::lockAtLeastFor)
        .containsExactly("ScheduledRiskLevelCheckJob_runFromAmlService", "1h", "30m");
  }

  @Test
  void existsWithoutAmlServiceEnabled() {
    new ApplicationContextRunner()
        .withUserConfiguration(ScheduledRiskLevelCheckJob.class)
        .withBean(RiskLevelService.class, () -> riskLevelService)
        .withBean(AmlRiskReader.class, () -> amlRiskReader)
        .withBean(TkfRiskReader.class, () -> tkfRiskReader)
        .withBean(AmlServiceProperties.class, () -> properties(false))
        .run(context -> assertThat(context).hasSingleBean(ScheduledRiskLevelCheckJob.class));
  }

  @Test
  void withoutAmlServiceRunsBothPillarsFromTheViewsAtOneAndNeverAsksAmlService() {
    job(false).run();

    verify(riskLevelService).runRiskLevelCheck(EXPECTED_PROBABILITY);
    verify(riskLevelService).runTkfRiskLevelCheck(EXPECTED_PROBABILITY);
    verifyNoMoreInteractions(riskLevelService);
    verifyNoInteractions(amlRiskReader, tkfRiskReader, eventPublisher);
  }

  @Test
  void withoutAmlServiceDoesNothingAtHalfPastFive() {
    job(false).runFromAmlService();

    verifyNoInteractions(riskLevelService, amlRiskReader, tkfRiskReader, eventPublisher);
  }

  @Test
  void withAmlServiceOnlyRefreshesTheViewsAtOneAndWritesNothing() {
    job(true).run();

    InOrder inOrder = inOrder(amlRiskReader, tkfRiskReader);
    inOrder.verify(amlRiskReader).refreshLatestResidencyView();
    inOrder.verify(amlRiskReader).refreshAmlRiskMetadataView();
    inOrder.verify(tkfRiskReader).refreshMaterializedView();
    verifyNoMoreInteractions(amlRiskReader, tkfRiskReader);
    verifyNoInteractions(riskLevelService, eventPublisher);
  }

  @Test
  void withAmlServiceTheOtherViewsStillRefreshAtOneWhenTheResidencyRefreshFails() {
    willThrow(new IllegalStateException("residency refresh failed"))
        .given(amlRiskReader)
        .refreshLatestResidencyView();

    job(true).run();

    verify(amlRiskReader).refreshAmlRiskMetadataView();
    verify(tkfRiskReader).refreshMaterializedView();
  }

  @Test
  void withAmlServiceTheTkfViewStillRefreshesAtOneWhenTheAmlRiskMetadataRefreshFails() {
    willThrow(new IllegalStateException("metadata refresh failed"))
        .given(amlRiskReader)
        .refreshAmlRiskMetadataView();

    job(true).run();

    verify(tkfRiskReader).refreshMaterializedView();
  }

  @Test
  void withAmlServiceTheThirdPillarViewsStillRefreshAtOneWhenTheTkfRefreshFails() {
    willThrow(new IllegalStateException("tkf refresh failed"))
        .given(tkfRiskReader)
        .refreshMaterializedView();

    job(true).run();

    verify(amlRiskReader).refreshLatestResidencyView();
    verify(amlRiskReader).refreshAmlRiskMetadataView();
    verifyNoInteractions(riskLevelService);
  }

  @Test
  void withoutAmlServiceTkfStillRunsAndNothingIsPostedWhenTheThirdPillarViewCheckFails() {
    willThrow(new IllegalStateException("view refresh failed"))
        .given(riskLevelService)
        .runRiskLevelCheck(anyDouble());

    job(false).run();

    verify(riskLevelService).runTkfRiskLevelCheck(EXPECTED_PROBABILITY);
    verifyNoInteractions(eventPublisher);
  }

  @Test
  void withoutAmlServiceTheThirdPillarStillRunsAndNothingIsPostedWhenTheTkfViewCheckFails() {
    willThrow(new IllegalStateException("view refresh failed"))
        .given(riskLevelService)
        .runTkfRiskLevelCheck(anyDouble());

    job(false).run();

    verify(riskLevelService).runRiskLevelCheck(EXPECTED_PROBABILITY);
    verifyNoInteractions(eventPublisher);
  }

  @Test
  void withAmlServiceWritesBothPillarsFromAmlServiceAtHalfPastFiveAndNeverTouchesTheViews() {
    given(riskLevelService.fetchThirdPillarRiskLevels()).willReturn(THIRD_PILLAR_RUN);
    given(riskLevelService.fetchTkfRiskLevels()).willReturn(TKF_RUN);

    job(true).runFromAmlService();

    verify(riskLevelService).writeThirdPillarRiskLevels(THIRD_PILLAR_RUN);
    verify(riskLevelService).writeTkfRiskLevels(TKF_RUN);
    verify(riskLevelService).fetchThirdPillarRiskLevels();
    verify(riskLevelService).fetchTkfRiskLevels();
    verifyNoMoreInteractions(riskLevelService);
    verifyNoInteractions(amlRiskReader, tkfRiskReader, eventPublisher);
  }

  @Test
  void withAmlServiceFallsBackToTheViewsFlowForTheTkfFetchThatFailed() {
    given(riskLevelService.fetchThirdPillarRiskLevels()).willReturn(THIRD_PILLAR_RUN);
    willThrow(new RiskLevelsRefusedException("Refusing: family=tkf"))
        .given(riskLevelService)
        .fetchTkfRiskLevels();

    job(true).runFromAmlService();

    verify(riskLevelService).writeThirdPillarRiskLevels(THIRD_PILLAR_RUN);
    verify(riskLevelService).runTkfRiskLevelCheck(EXPECTED_PROBABILITY);
    verify(riskLevelService, never()).writeTkfRiskLevels(any());
    verify(riskLevelService, never()).runRiskLevelCheck(anyDouble());
    verifyNoInteractions(amlRiskReader, tkfRiskReader);
    verify(eventPublisher)
        .publishEvent(new AmlRiskLevelJobFellBackEvent("TKF", "Refusing: family=tkf"));
    verifyNoMoreInteractions(eventPublisher);
  }

  @Test
  void withAmlServiceFallsBackToTheViewsFlowForTheThirdPillarFetchThatFailed() {
    willThrow(new RiskLevelsRefusedException("Refusing: family=third-pillar"))
        .given(riskLevelService)
        .fetchThirdPillarRiskLevels();
    given(riskLevelService.fetchTkfRiskLevels()).willReturn(TKF_RUN);

    job(true).runFromAmlService();

    verify(riskLevelService).runRiskLevelCheck(EXPECTED_PROBABILITY);
    verify(riskLevelService).writeTkfRiskLevels(TKF_RUN);
    verify(riskLevelService, never()).runTkfRiskLevelCheck(anyDouble());
    verify(eventPublisher)
        .publishEvent(
            new AmlRiskLevelJobFellBackEvent("III pillar", "Refusing: family=third-pillar"));
    verifyNoMoreInteractions(eventPublisher);
  }

  @Test
  void withAmlServiceAFailedWriteIsReportedAsFailedAndDoesNotFallBack() {
    given(riskLevelService.fetchThirdPillarRiskLevels()).willReturn(THIRD_PILLAR_RUN);
    given(riskLevelService.fetchTkfRiskLevels()).willReturn(TKF_RUN);
    willThrow(new IllegalStateException("database unavailable"))
        .given(riskLevelService)
        .writeTkfRiskLevels(TKF_RUN);

    job(true).runFromAmlService();

    verify(riskLevelService, never()).runTkfRiskLevelCheck(anyDouble());
    verify(riskLevelService).writeThirdPillarRiskLevels(THIRD_PILLAR_RUN);
    verify(eventPublisher)
        .publishEvent(new AmlRiskLevelJobFailedEvent("TKF", "IllegalStateException"));
    verifyNoMoreInteractions(eventPublisher);
  }

  @Test
  void withAmlServiceReportsTheFallbacksOwnFailureWhenItFailsToo() {
    given(riskLevelService.fetchThirdPillarRiskLevels()).willReturn(THIRD_PILLAR_RUN);
    willThrow(new RiskLevelsRefusedException("Refusing: family=tkf"))
        .given(riskLevelService)
        .fetchTkfRiskLevels();
    willThrow(new IllegalStateException("view read failed"))
        .given(riskLevelService)
        .runTkfRiskLevelCheck(anyDouble());

    job(true).runFromAmlService();

    verify(eventPublisher)
        .publishEvent(new AmlRiskLevelJobFellBackEvent("TKF", "Refusing: family=tkf"));
    verify(eventPublisher)
        .publishEvent(new AmlRiskLevelJobFailedEvent("TKF", "IllegalStateException"));
    verify(riskLevelService).writeThirdPillarRiskLevels(THIRD_PILLAR_RUN);
  }

  @Test
  void withAmlServiceStillFallsBackAndRunsTkfWhenTheFallbackCannotBeReported() {
    willThrow(new RiskLevelsRefusedException("Refusing: family=third-pillar"))
        .given(riskLevelService)
        .fetchThirdPillarRiskLevels();
    given(riskLevelService.fetchTkfRiskLevels()).willReturn(TKF_RUN);
    willThrow(new IllegalStateException("Slack unavailable"))
        .given(eventPublisher)
        .publishEvent(any(Object.class));

    job(true).runFromAmlService();

    verify(riskLevelService).runRiskLevelCheck(EXPECTED_PROBABILITY);
    verify(riskLevelService).writeTkfRiskLevels(TKF_RUN);
  }

  @Test
  void reportsOnlyTheStatusOfAServerErrorWhoseBodyNamesAPersonAndAToken() {
    willThrow(errorFromAmlService(INTERNAL_SERVER_ERROR))
        .given(riskLevelService)
        .fetchTkfRiskLevels();

    job(true).runFromAmlService();

    verify(eventPublisher).publishEvent(new AmlRiskLevelJobFellBackEvent("TKF", "HTTP 500"));
  }

  @Test
  void reportsOnlyTheStatusOfAClientErrorWhoseBodyNamesAPersonAndAToken() {
    willThrow(errorFromAmlService(NOT_FOUND)).given(riskLevelService).fetchTkfRiskLevels();

    job(true).runFromAmlService();

    verify(eventPublisher).publishEvent(new AmlRiskLevelJobFellBackEvent("TKF", "HTTP 404"));
  }

  @Test
  void reportsOnlyTheClassOfATimeout() {
    willThrow(
            new ResourceAccessException(
                "I/O error on GET request for \"https://aml-service.test/v1/risk-levels/tkf\":"
                    + " request timed out"))
        .given(riskLevelService)
        .fetchTkfRiskLevels();

    job(true).runFromAmlService();

    verify(eventPublisher)
        .publishEvent(new AmlRiskLevelJobFellBackEvent("TKF", "ResourceAccessException"));
  }

  @Test
  void reportsOnlyTheClassOfAnUnexpectedFailure() {
    willThrow(new IllegalStateException("anything at all"))
        .given(riskLevelService)
        .fetchThirdPillarRiskLevels();

    job(true).runFromAmlService();

    verify(eventPublisher)
        .publishEvent(new AmlRiskLevelJobFellBackEvent("III pillar", "IllegalStateException"));
  }

  private ScheduledRiskLevelCheckJob job(boolean amlServiceEnabled) {
    return new ScheduledRiskLevelCheckJob(
        riskLevelService,
        amlRiskReader,
        tkfRiskReader,
        eventPublisher,
        properties(amlServiceEnabled));
  }

  private static AmlServiceProperties properties(boolean enabled) {
    return new AmlServiceProperties(
        enabled,
        "https://aml-service.test",
        "test-api-token-of-at-least-thirty-two-characters",
        ofSeconds(5),
        ofSeconds(60));
  }

  private static RestClientResponseException errorFromAmlService(HttpStatus status) {
    var builder = RestClient.builder().baseUrl("https://aml-service.test");
    var server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("https://aml-service.test/v1/risk-levels/tkf"))
        .andRespond(
            withStatus(status)
                .contentType(APPLICATION_JSON)
                .body(
                    "{\"personalCode\": \"38888888888\","
                        + " \"authorization\": \"Bearer test-api-token-of-at-least-thirty-two\"}"));
    return catchThrowableOfType(
        RestClientResponseException.class,
        () -> builder.build().get().uri("/v1/risk-levels/tkf").retrieve().toBodilessEntity());
  }
}
