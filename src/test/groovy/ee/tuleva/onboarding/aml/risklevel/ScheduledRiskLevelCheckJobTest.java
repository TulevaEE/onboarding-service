package ee.tuleva.onboarding.aml.risklevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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
import ee.tuleva.onboarding.time.ClockConfig;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class ScheduledRiskLevelCheckJobTest {

  private static final AssessedRiskLevels THIRD_PILLAR_RUN =
      new AssessedRiskLevels(Instant.parse("2026-10-28T02:45:00Z"), List.of());
  private static final AssessedRiskLevels TKF_RUN =
      new AssessedRiskLevels(Instant.parse("2026-10-28T02:15:00Z"), List.of());

  private final RiskLevelService riskLevelService = mock(RiskLevelService.class);
  private final AmlServiceRiskLevels amlServiceRiskLevels = mock(AmlServiceRiskLevels.class);
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  private final ScheduledRiskLevelCheckJob job =
      new ScheduledRiskLevelCheckJob(riskLevelService, amlServiceRiskLevels, eventPublisher);

  @Test
  void runsAfterTheNightlyAmlServiceBatchRunsAndOverrideImport() throws NoSuchMethodException {
    Scheduled scheduled =
        ScheduledRiskLevelCheckJob.class
            .getMethod("runFromAmlService")
            .getAnnotation(Scheduled.class);

    assertThat(scheduled.cron()).isEqualTo("0 30 5 * * ?");
    assertThat(scheduled.zone()).isEqualTo("Europe/Tallinn");
  }

  @Test
  void keepsItsLockThatExpiresWellBeforeItsNextRun() throws NoSuchMethodException {
    SchedulerLock lock =
        ScheduledRiskLevelCheckJob.class
            .getMethod("runFromAmlService")
            .getAnnotation(SchedulerLock.class);

    assertThat(lock)
        .extracting(
            SchedulerLock::name, SchedulerLock::lockAtMostFor, SchedulerLock::lockAtLeastFor)
        .containsExactly("ScheduledRiskLevelCheckJob_runFromAmlService", "1h", "30m");
  }

  @ParameterizedTest
  @ValueSource(strings = {"production", "test", "ci,test", "pg,test"})
  void existsInEveryProfileThatHasAnAmlService(String profiles) {
    contextRunner()
        .withPropertyValues("spring.profiles.active=" + profiles)
        .run(context -> assertThat(context).hasSingleBean(ScheduledRiskLevelCheckJob.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"dev", "staging"})
  void neitherRunsNorAsksAmlServiceInDevAndStaging(String profile) {
    contextRunner()
        .withPropertyValues("spring.profiles.active=" + profile)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(ScheduledRiskLevelCheckJob.class);
              assertThat(context).doesNotHaveBean(AmlServiceRiskLevels.class);
              assertThat(context).doesNotHaveBean(AmlServiceClient.class);
            });
  }

  @Test
  void writesBothPillarsFromAmlService() {
    given(amlServiceRiskLevels.fetchThirdPillar()).willReturn(THIRD_PILLAR_RUN);
    given(amlServiceRiskLevels.fetchTkf()).willReturn(TKF_RUN);

    job.runFromAmlService();

    verify(riskLevelService).writeThirdPillarRiskLevels(THIRD_PILLAR_RUN);
    verify(riskLevelService).writeTkfRiskLevels(TKF_RUN);
    verifyNoMoreInteractions(riskLevelService);
    verifyNoInteractions(eventPublisher);
  }

  @Test
  void reportsAFailedTkfFetchAsFailedAndStillWritesTheThirdPillar() {
    given(amlServiceRiskLevels.fetchThirdPillar()).willReturn(THIRD_PILLAR_RUN);
    willThrow(new RiskLevelsRefusedException("Refusing: family=tkf"))
        .given(amlServiceRiskLevels)
        .fetchTkf();

    job.runFromAmlService();

    verify(riskLevelService).writeThirdPillarRiskLevels(THIRD_PILLAR_RUN);
    verify(riskLevelService, never()).writeTkfRiskLevels(any());
    verify(eventPublisher)
        .publishEvent(new AmlRiskLevelJobFailedEvent("TKF", "Refusing: family=tkf"));
    verifyNoMoreInteractions(eventPublisher);
  }

  @Test
  void reportsAFailedThirdPillarFetchAsFailedAndStillWritesTkf() {
    willThrow(new RiskLevelsRefusedException("Refusing: family=third-pillar"))
        .given(amlServiceRiskLevels)
        .fetchThirdPillar();
    given(amlServiceRiskLevels.fetchTkf()).willReturn(TKF_RUN);

    job.runFromAmlService();

    verify(riskLevelService, never()).writeThirdPillarRiskLevels(any());
    verify(riskLevelService).writeTkfRiskLevels(TKF_RUN);
    verify(eventPublisher)
        .publishEvent(
            new AmlRiskLevelJobFailedEvent("III pillar", "Refusing: family=third-pillar"));
    verifyNoMoreInteractions(eventPublisher);
  }

  @Test
  void reportsAFailedWriteAsFailedAndStillWritesTheOtherPillar() {
    given(amlServiceRiskLevels.fetchThirdPillar()).willReturn(THIRD_PILLAR_RUN);
    given(amlServiceRiskLevels.fetchTkf()).willReturn(TKF_RUN);
    willThrow(new IllegalStateException("database unavailable"))
        .given(riskLevelService)
        .writeThirdPillarRiskLevels(THIRD_PILLAR_RUN);

    job.runFromAmlService();

    verify(riskLevelService).writeTkfRiskLevels(TKF_RUN);
    verify(eventPublisher)
        .publishEvent(new AmlRiskLevelJobFailedEvent("III pillar", "IllegalStateException"));
    verifyNoMoreInteractions(eventPublisher);
  }

  @Test
  void stillWritesTkfWhenTheThirdPillarsFailureCannotBeReported() {
    willThrow(new RiskLevelsRefusedException("Refusing: family=third-pillar"))
        .given(amlServiceRiskLevels)
        .fetchThirdPillar();
    given(amlServiceRiskLevels.fetchTkf()).willReturn(TKF_RUN);
    willThrow(new IllegalStateException("Slack unavailable"))
        .given(eventPublisher)
        .publishEvent(any(Object.class));

    job.runFromAmlService();

    verify(riskLevelService).writeTkfRiskLevels(TKF_RUN);
  }

  @Test
  void reportsOnlyTheStatusOfAServerErrorWhoseBodyNamesAPersonAndAToken() {
    willThrow(errorFromAmlService(INTERNAL_SERVER_ERROR)).given(amlServiceRiskLevels).fetchTkf();

    job.runFromAmlService();

    verify(eventPublisher).publishEvent(new AmlRiskLevelJobFailedEvent("TKF", "HTTP 500"));
  }

  @Test
  void reportsOnlyTheStatusOfAClientErrorWhoseBodyNamesAPersonAndAToken() {
    willThrow(errorFromAmlService(NOT_FOUND)).given(amlServiceRiskLevels).fetchTkf();

    job.runFromAmlService();

    verify(eventPublisher).publishEvent(new AmlRiskLevelJobFailedEvent("TKF", "HTTP 404"));
  }

  @Test
  void reportsOnlyTheClassOfATimeout() {
    willThrow(
            new ResourceAccessException(
                "I/O error on GET request for \"https://aml-service.test/v1/risk-levels/tkf\":"
                    + " request timed out"))
        .given(amlServiceRiskLevels)
        .fetchTkf();

    job.runFromAmlService();

    verify(eventPublisher)
        .publishEvent(new AmlRiskLevelJobFailedEvent("TKF", "ResourceAccessException"));
  }

  @Test
  void reportsOnlyTheClassOfAnUnexpectedFailure() {
    willThrow(new IllegalStateException("anything at all"))
        .given(amlServiceRiskLevels)
        .fetchThirdPillar();

    job.runFromAmlService();

    verify(eventPublisher)
        .publishEvent(new AmlRiskLevelJobFailedEvent("III pillar", "IllegalStateException"));
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(
            ClockConfig.class,
            AmlServiceConfiguration.class,
            AmlServiceRiskLevels.class,
            ScheduledRiskLevelCheckJob.class)
        .withBean(RiskLevelService.class, () -> riskLevelService)
        .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
        .withPropertyValues(
            "aml-service.url=https://aml-service.test",
            "aml-service.connect-timeout=5s",
            "aml-service.read-timeout=60s",
            "aml-service.high-risk-connect-timeout=1s",
            "aml-service.high-risk-read-timeout=2s",
            "aml-service.kyc-onboarding-connect-timeout=1s",
            "aml-service.kyc-onboarding-read-timeout=3s");
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
