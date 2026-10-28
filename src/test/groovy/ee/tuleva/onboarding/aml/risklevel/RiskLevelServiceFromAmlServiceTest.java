package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.aml.notification.AmlCheckCreatedEvent;
import ee.tuleva.onboarding.aml.notification.AmlRiskLevelJobRunEvent;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.client.ResourceAccessException;

class RiskLevelServiceFromAmlServiceTest {

  private static final Instant TODAYS_RUN = Instant.parse("2020-01-01T02:15:00.123456Z");
  private static final Instant YESTERDAYS_RUN = Instant.parse("2019-12-31T02:15:00.123456Z");

  private final AmlRiskReader amlRiskReader = mock(AmlRiskReader.class);
  private final TkfRiskReader tkfRiskReader = mock(TkfRiskReader.class);
  private final AmlServiceClient amlServiceClient = mock(AmlServiceClient.class);
  private final AmlCheckRepository amlCheckRepository = mock(AmlCheckRepository.class);
  private final List<Object> publishedEvents = new ArrayList<>();
  private final ApplicationEventPublisher eventPublisher = publishedEvents::add;
  private final RiskLevelService riskLevelService = serviceAt(TestClockHolder.clock);

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(TestClockHolder.clock);
    given(amlCheckRepository.save(any(AmlCheck.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void writesAFailedRiskLevelCheckForAHighResultOfTodaysRun() {
    givenRun("third-pillar", TODAYS_RUN, new RiskLevelResult("38888888888", 1, Map.of("key", 999)));

    riskLevelService.writeThirdPillarRiskLevels(riskLevelService.fetchThirdPillarRiskLevels());

    verify(amlCheckRepository)
        .save(failedCheck("38888888888", RISK_LEVEL, Map.of("key", 999, "level", 1)));
    assertThat(createdCheckEvents()).hasSize(1);
    assertJobRun("III pillar", 1, 0, 1);
  }

  @Test
  void writesAFailedRiskLevelCheckForASampledMediumResult() {
    givenRun("third-pillar", TODAYS_RUN, new RiskLevelResult("49999999990", 2, Map.of("key", 777)));

    riskLevelService.writeThirdPillarRiskLevels(riskLevelService.fetchThirdPillarRiskLevels());

    verify(amlCheckRepository)
        .save(failedCheck("49999999990", RISK_LEVEL, Map.of("key", 777, "level", 2)));
    assertThat(createdCheckEvents()).hasSize(1);
    assertJobRun("III pillar", 0, 1, 1);
  }

  @Test
  void countsHighAndSampledMediumResultsApart() {
    givenRun(
        "third-pillar",
        TODAYS_RUN,
        new RiskLevelResult("38888888888", 1, Map.of("key", 999)),
        new RiskLevelResult("49999999990", 2, Map.of("key", 777)),
        new RiskLevelResult("49999999991", 2, Map.of("key", 778)));

    riskLevelService.writeThirdPillarRiskLevels(riskLevelService.fetchThirdPillarRiskLevels());

    assertThat(createdCheckEvents()).hasSize(3);
    assertJobRun("III pillar", 1, 2, 3);
  }

  @Test
  void skipsAResultWithoutAPersonalCode() {
    givenRun("third-pillar", TODAYS_RUN, new RiskLevelResult("   ", 1, Map.of()));

    riskLevelService.writeThirdPillarRiskLevels(riskLevelService.fetchThirdPillarRiskLevels());

    verify(amlCheckRepository, never()).save(any());
    assertThat(createdCheckEvents()).isEmpty();
    assertJobRun("III pillar", 1, 0, 0);
  }

  @Test
  void skipsAResultWhoseMetadataAFailedCheckOfTheLastSixMonthsAlreadyHas() {
    givenRun(
        "third-pillar", TODAYS_RUN, new RiskLevelResult("38888888888", 1, Map.of("abc", "123")));
    givenFailedCheck(
        "38888888888",
        RISK_LEVEL,
        Map.of("abc", "123", "level", 1),
        TestClockHolder.now.minus(90, DAYS));

    riskLevelService.writeThirdPillarRiskLevels(riskLevelService.fetchThirdPillarRiskLevels());

    verify(amlCheckRepository, never()).save(any());
    assertThat(createdCheckEvents()).isEmpty();
    assertJobRun("III pillar", 1, 0, 0);
  }

  @Test
  void skipsAResultWhoseMetadataDiffersOnlyInVersion() {
    givenRun(
        "third-pillar",
        TODAYS_RUN,
        new RiskLevelResult("38888888888", 1, Map.of("abc", "123", "version", "2.0")));
    givenFailedCheck(
        "38888888888",
        RISK_LEVEL,
        Map.of("abc", "123", "level", 1, "version", "1.0"),
        TestClockHolder.now.minus(90, DAYS));

    riskLevelService.writeThirdPillarRiskLevels(riskLevelService.fetchThirdPillarRiskLevels());

    verify(amlCheckRepository, never()).save(any());
    assertJobRun("III pillar", 1, 0, 0);
  }

  @Test
  void writesACheckWhenTheMetadataDiffersInMoreThanVersion() {
    givenRun(
        "third-pillar",
        TODAYS_RUN,
        new RiskLevelResult("38888888888", 1, Map.of("abc", "456", "version", "2.0")));
    givenFailedCheck(
        "38888888888",
        RISK_LEVEL,
        Map.of("abc", "123", "level", 1, "version", "2.0"),
        TestClockHolder.now.minus(90, DAYS));

    riskLevelService.writeThirdPillarRiskLevels(riskLevelService.fetchThirdPillarRiskLevels());

    verify(amlCheckRepository)
        .save(
            failedCheck(
                "38888888888", RISK_LEVEL, Map.of("abc", "456", "version", "2.0", "level", 1)));
    assertJobRun("III pillar", 1, 0, 1);
  }

  @Test
  void reportsARunWithoutResults() {
    givenRun("third-pillar", TODAYS_RUN);

    riskLevelService.writeThirdPillarRiskLevels(riskLevelService.fetchThirdPillarRiskLevels());

    verify(amlCheckRepository, never()).save(any());
    assertThat(createdCheckEvents()).isEmpty();
    assertJobRun("III pillar", 0, 0, 0);
  }

  @Test
  void writesATkfRiskLevelCheckForATkfResult() {
    givenRun(
        "tkf",
        TODAYS_RUN,
        new RiskLevelResult("38501010001", 1, Map.of("level", 1, "total_points", 105)));

    riskLevelService.writeTkfRiskLevels(riskLevelService.fetchTkfRiskLevels());

    verify(amlCheckRepository)
        .save(failedCheck("38501010001", TKF_RISK_LEVEL, Map.of("level", 1, "total_points", 105)));
    assertJobRun("TKF", 1, 0, 1);
  }

  @Test
  void skipsATkfResultAlreadyWrittenWithinSixMonths() {
    givenRun(
        "tkf",
        TODAYS_RUN,
        new RiskLevelResult("38501010001", 1, Map.of("level", 1, "total_points", 105)));
    givenFailedCheck(
        "38501010001",
        TKF_RISK_LEVEL,
        Map.of("level", 1, "total_points", 105),
        TestClockHolder.now.minus(90, DAYS));

    riskLevelService.writeTkfRiskLevels(riskLevelService.fetchTkfRiskLevels());

    verify(amlCheckRepository, never()).save(any());
    assertJobRun("TKF", 1, 0, 0);
  }

  @Test
  void refusesAnotherDaysRunAndWritesNothing() {
    givenRun(
        "third-pillar", YESTERDAYS_RUN, new RiskLevelResult("38888888888", 1, Map.of("key", 999)));

    assertThatThrownBy(riskLevelService::fetchThirdPillarRiskLevels)
        .isInstanceOf(RiskLevelsRefusedException.class);

    verifyNoInteractions(amlCheckRepository);
    assertThat(publishedEvents).isEmpty();
  }

  @Test
  void refusesYesterdaysTallinnRunAlthoughItIsOfTheSameUtcDate() {
    RiskLevelService justAfterTallinnMidnight =
        serviceAt(Clock.fixed(Instant.parse("2026-10-27T22:30:00Z"), UTC));
    givenRun(
        "tkf",
        Instant.parse("2026-10-27T02:15:00Z"),
        new RiskLevelResult("38501010001", 1, Map.of("level", 1)));

    assertThatThrownBy(justAfterTallinnMidnight::fetchTkfRiskLevels)
        .isInstanceOf(RiskLevelsRefusedException.class);

    verifyNoInteractions(amlCheckRepository);
    assertThat(publishedEvents).isEmpty();
  }

  @Test
  void acceptsTodaysTallinnRunAlthoughItIsOfTheUtcDateBefore() {
    RiskLevelService atTheJobsTime =
        serviceAt(Clock.fixed(Instant.parse("2026-10-28T03:30:00Z"), UTC));
    givenRun(
        "tkf",
        Instant.parse("2026-10-27T22:30:00Z"),
        new RiskLevelResult("38501010001", 1, Map.of("level", 1)));

    atTheJobsTime.writeTkfRiskLevels(atTheJobsTime.fetchTkfRiskLevels());

    verify(amlCheckRepository).save(failedCheck("38501010001", TKF_RISK_LEVEL, Map.of("level", 1)));
    assertJobRun("TKF", 1, 0, 1);
  }

  @Test
  void writesNothingWhenAmlServiceDoesNotAnswer() {
    willThrow(new ResourceAccessException("request timed out"))
        .given(amlServiceClient)
        .riskLevels("tkf");

    assertThatThrownBy(riskLevelService::fetchTkfRiskLevels)
        .isInstanceOf(ResourceAccessException.class);

    verifyNoInteractions(amlCheckRepository);
    assertThat(publishedEvents).isEmpty();
  }

  @Test
  void neverTouchesTheViews() {
    givenRun("third-pillar", TODAYS_RUN, new RiskLevelResult("38888888888", 1, Map.of("key", 999)));
    givenRun("tkf", TODAYS_RUN, new RiskLevelResult("38888888888", 1, Map.of("key", 999)));

    riskLevelService.writeThirdPillarRiskLevels(riskLevelService.fetchThirdPillarRiskLevels());
    riskLevelService.writeTkfRiskLevels(riskLevelService.fetchTkfRiskLevels());

    verifyNoInteractions(amlRiskReader, tkfRiskReader);
  }

  private RiskLevelService serviceAt(Clock clock) {
    ClockHolder.setClock(clock);
    return new RiskLevelService(
        amlRiskReader, tkfRiskReader, amlServiceClient, amlCheckRepository, eventPublisher, clock);
  }

  private void givenRun(String family, Instant assessedAt, RiskLevelResult... results) {
    given(amlServiceClient.riskLevels(family))
        .willReturn(new AssessedRiskLevels(assessedAt, List.of(results)));
  }

  private void givenFailedCheck(
      String personalCode, AmlCheckType type, Map<String, Object> metadata, Instant createdTime) {
    given(
            amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
                eq(personalCode), eq(type), any(Instant.class)))
        .willReturn(
            List.of(
                AmlCheck.builder()
                    .personalCode(personalCode)
                    .type(type)
                    .success(false)
                    .metadata(metadata)
                    .createdTime(createdTime)
                    .build()));
  }

  private static AmlCheck failedCheck(
      String personalCode, AmlCheckType type, Map<String, Object> metadata) {
    return AmlCheck.builder()
        .personalCode(personalCode)
        .type(type)
        .success(false)
        .metadata(metadata)
        .build();
  }

  private List<Object> createdCheckEvents() {
    return publishedEvents.stream().filter(AmlCheckCreatedEvent.class::isInstance).toList();
  }

  private void assertJobRun(String label, int high, int medium, int created) {
    assertThat(publishedEvents)
        .filteredOn(AmlRiskLevelJobRunEvent.class::isInstance)
        .singleElement()
        .extracting("label", "highRiskRowCount", "mediumRiskRowCount", "amlChecksCreatedCount")
        .containsExactly(label, high, medium, created);
  }
}
