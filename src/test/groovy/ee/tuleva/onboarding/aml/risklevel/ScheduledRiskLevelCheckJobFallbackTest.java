package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.AmlCheckType.TKF_RISK_LEVEL;
import static java.time.Duration.ofSeconds;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.notification.AmlCheckCreatedEvent;
import ee.tuleva.onboarding.aml.notification.AmlRiskLevelJobFailedEvent;
import ee.tuleva.onboarding.aml.notification.AmlRiskLevelJobFellBackEvent;
import ee.tuleva.onboarding.aml.notification.AmlRiskLevelJobRunEvent;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;

class ScheduledRiskLevelCheckJobFallbackTest {

  private static final Clock AT_HALF_PAST_FIVE =
      Clock.fixed(Instant.parse("2026-10-28T03:30:00Z"), UTC);
  private static final Instant TODAYS_TKF_RUN = Instant.parse("2026-10-28T02:15:00Z");
  private static final Instant TODAYS_THIRD_PILLAR_RUN = Instant.parse("2026-10-28T02:45:00Z");
  private static final Instant YESTERDAYS_TKF_RUN = Instant.parse("2026-10-27T02:15:00Z");
  private static final double SAMPLING_PROBABILITY = 0.025 / 30.0;

  private final AmlRiskReader amlRiskReader = mock(AmlRiskReader.class);
  private final TkfRiskReader tkfRiskReader = mock(TkfRiskReader.class);
  private final AmlServiceClient amlServiceClient = mock(AmlServiceClient.class);
  private final AmlCheckRepository amlCheckRepository = mock(AmlCheckRepository.class);
  private final List<Object> publishedEvents = new ArrayList<>();
  private boolean slackRejectsCreatedChecks;
  private final ApplicationEventPublisher eventPublisher =
      event -> {
        if (slackRejectsCreatedChecks && event instanceof AmlCheckCreatedEvent) {
          throw HttpClientErrorException.create(
              TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null);
        }
        publishedEvents.add(event);
      };
  private final RiskLevelService riskLevelService =
      new RiskLevelService(amlRiskReader, tkfRiskReader, amlCheckRepository, eventPublisher);
  private final ScheduledRiskLevelCheckJob job =
      new ScheduledRiskLevelCheckJob(
          riskLevelService,
          new AmlServiceRiskLevels(amlServiceClient, AT_HALF_PAST_FIVE),
          amlRiskReader,
          tkfRiskReader,
          eventPublisher,
          new AmlServiceProperties(
              true,
              "https://aml-service.test",
              "test-api-token-of-at-least-thirty-two-characters",
              ofSeconds(5),
              ofSeconds(60)));

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(AT_HALF_PAST_FIVE);
    given(amlCheckRepository.save(any(AmlCheck.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void aSlackFailureWhileWritingIsReportedAsFailedAndNeverFallsBackToTheViews() {
    givenRun("third-pillar", TODAYS_THIRD_PILLAR_RUN);
    givenRun("tkf", TODAYS_TKF_RUN);
    slackRejectsCreatedChecks = true;

    job.runFromAmlService();

    assertThat(publishedEvents)
        .containsExactly(
            new AmlRiskLevelJobFailedEvent("III pillar", "HTTP 429"),
            new AmlRiskLevelJobFailedEvent("TKF", "HTTP 429"));
    verifyNoInteractions(amlRiskReader, tkfRiskReader);
  }

  @Test
  void anotherDaysRunFallsBackToTheViewsFlowRefreshingTheViewsBeforeReadingThem() {
    givenRun("third-pillar", TODAYS_THIRD_PILLAR_RUN);
    givenRun("tkf", YESTERDAYS_TKF_RUN);

    job.runFromAmlService();

    assertThat(publishedEvents)
        .filteredOn(AmlRiskLevelJobFellBackEvent.class::isInstance)
        .extracting(event -> ((AmlRiskLevelJobFellBackEvent) event).label())
        .containsExactly("TKF");
    assertThat(publishedEvents)
        .filteredOn(AmlRiskLevelJobRunEvent.class::isInstance)
        .extracting(event -> ((AmlRiskLevelJobRunEvent) event).getLabel())
        .containsExactly("III pillar", "TKF");
    InOrder inOrder = inOrder(tkfRiskReader);
    inOrder.verify(tkfRiskReader).refreshMaterializedView();
    inOrder.verify(tkfRiskReader).getHighRiskRows();
    inOrder.verify(tkfRiskReader).getMediumRiskRowsSample(SAMPLING_PROBABILITY);
    verifyNoInteractions(amlRiskReader);
  }

  @Test
  void aFallbackWhoseViewRefreshFailsWritesNothingAndIsReportedAsFailed() {
    givenRun("third-pillar", TODAYS_THIRD_PILLAR_RUN);
    givenRun("tkf", YESTERDAYS_TKF_RUN);
    willThrow(new IllegalStateException("refresh failed"))
        .given(tkfRiskReader)
        .refreshMaterializedView();

    job.runFromAmlService();

    assertThat(publishedEvents)
        .filteredOn(
            event ->
                event instanceof AmlRiskLevelJobFellBackEvent
                    || event instanceof AmlRiskLevelJobFailedEvent)
        .extracting(Object::getClass)
        .containsExactly(AmlRiskLevelJobFellBackEvent.class, AmlRiskLevelJobFailedEvent.class);
    assertThat(publishedEvents)
        .contains(new AmlRiskLevelJobFailedEvent("TKF", "IllegalStateException"));
    verify(tkfRiskReader, never()).getHighRiskRows();
    verify(amlCheckRepository, never())
        .save(argThat((AmlCheck check) -> check.getType() == TKF_RISK_LEVEL));
  }

  private void givenRun(String family, Instant assessedAt) {
    given(amlServiceClient.riskLevels(family))
        .willReturn(
            new AssessedRiskLevels(
                assessedAt,
                List.of(new RiskLevelResult("38888888888", 1, Map.of("version", "1")))));
  }
}
