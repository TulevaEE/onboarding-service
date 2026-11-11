package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.AmlCheckType.RISK_LEVEL;
import static ee.tuleva.onboarding.aml.AmlCheckType.TKF_RISK_LEVEL;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.notification.AmlCheckCreatedEvent;
import ee.tuleva.onboarding.aml.notification.AmlRiskLevelJobFailedEvent;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;

class ScheduledRiskLevelCheckJobFailureReportTest {

  private static final Clock AT_HALF_PAST_FIVE =
      Clock.fixed(Instant.parse("2026-10-28T03:30:00Z"), UTC);
  private static final Instant TODAYS_TKF_RUN = Instant.parse("2026-10-28T02:15:00Z");
  private static final Instant TODAYS_THIRD_PILLAR_RUN = Instant.parse("2026-10-28T02:45:00Z");
  private static final Instant YESTERDAYS_TKF_RUN = Instant.parse("2026-10-27T02:15:00Z");

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
  private final ScheduledRiskLevelCheckJob job =
      new ScheduledRiskLevelCheckJob(
          new RiskLevelService(amlCheckRepository, eventPublisher),
          new AmlServiceRiskLevels(amlServiceClient, AT_HALF_PAST_FIVE),
          eventPublisher);

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
  void aSlackFailureWhileWritingIsReportedAsFailed() {
    givenRun("third-pillar", TODAYS_THIRD_PILLAR_RUN);
    givenRun("tkf", TODAYS_TKF_RUN);
    slackRejectsCreatedChecks = true;

    job.runFromAmlService();

    assertThat(publishedEvents)
        .containsExactly(
            new AmlRiskLevelJobFailedEvent("III pillar", "HTTP 429"),
            new AmlRiskLevelJobFailedEvent("TKF", "HTTP 429"));
  }

  @Test
  void anotherDaysRunIsReportedAsFailedAndWritesNothingForItsPillar() {
    givenRun("third-pillar", TODAYS_THIRD_PILLAR_RUN);
    givenRun("tkf", YESTERDAYS_TKF_RUN);

    job.runFromAmlService();

    assertThat(publishedEvents)
        .filteredOn(AmlRiskLevelJobFailedEvent.class::isInstance)
        .extracting(event -> ((AmlRiskLevelJobFailedEvent) event).label())
        .containsExactly("TKF");
    assertThat(publishedEvents)
        .filteredOn(AmlRiskLevelJobRunEvent.class::isInstance)
        .extracting(event -> ((AmlRiskLevelJobRunEvent) event).getLabel())
        .containsExactly("III pillar");
    verify(amlCheckRepository).save(argThat((AmlCheck check) -> check.getType() == RISK_LEVEL));
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
