package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class RiskLevelServiceFromAmlServiceTest {

  private static final Instant TODAYS_RUN = Instant.parse("2020-01-01T02:15:00.123456Z");

  private final AmlRiskReader amlRiskReader = mock(AmlRiskReader.class);
  private final TkfRiskReader tkfRiskReader = mock(TkfRiskReader.class);
  private final AmlCheckRepository amlCheckRepository = mock(AmlCheckRepository.class);
  private final List<Object> publishedEvents = new ArrayList<>();
  private final ApplicationEventPublisher eventPublisher = publishedEvents::add;
  private final RiskLevelService riskLevelService =
      new RiskLevelService(amlRiskReader, tkfRiskReader, amlCheckRepository, eventPublisher);

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
    AssessedRiskLevels thirdPillarRun =
        todaysRun(new RiskLevelResult("38888888888", 1, Map.of("key", 999)));

    riskLevelService.writeThirdPillarRiskLevels(thirdPillarRun);

    verify(amlCheckRepository)
        .save(failedCheck("38888888888", RISK_LEVEL, Map.of("key", 999, "level", 1)));
    assertThat(createdCheckEvents()).hasSize(1);
    assertJobRun("III pillar", 1, 0, 1);
  }

  @Test
  void writesAFailedRiskLevelCheckForASampledMediumResult() {
    AssessedRiskLevels thirdPillarRun =
        todaysRun(new RiskLevelResult("49999999990", 2, Map.of("key", 777)));

    riskLevelService.writeThirdPillarRiskLevels(thirdPillarRun);

    verify(amlCheckRepository)
        .save(failedCheck("49999999990", RISK_LEVEL, Map.of("key", 777, "level", 2)));
    assertThat(createdCheckEvents()).hasSize(1);
    assertJobRun("III pillar", 0, 1, 1);
  }

  @Test
  void countsHighAndSampledMediumResultsApart() {
    AssessedRiskLevels thirdPillarRun =
        todaysRun(
            new RiskLevelResult("38888888888", 1, Map.of("key", 999)),
            new RiskLevelResult("49999999990", 2, Map.of("key", 777)),
            new RiskLevelResult("49999999991", 2, Map.of("key", 778)));

    riskLevelService.writeThirdPillarRiskLevels(thirdPillarRun);

    assertThat(createdCheckEvents()).hasSize(3);
    assertJobRun("III pillar", 1, 2, 3);
  }

  @Test
  void skipsAResultWithoutAPersonalCode() {
    AssessedRiskLevels thirdPillarRun = todaysRun(new RiskLevelResult("   ", 1, Map.of()));

    riskLevelService.writeThirdPillarRiskLevels(thirdPillarRun);

    verify(amlCheckRepository, never()).save(any());
    assertThat(createdCheckEvents()).isEmpty();
    assertJobRun("III pillar", 1, 0, 0);
  }

  @Test
  void skipsAResultWhoseMetadataAFailedCheckOfTheLastSixMonthsAlreadyHas() {
    AssessedRiskLevels thirdPillarRun =
        todaysRun(new RiskLevelResult("38888888888", 1, Map.of("abc", "123")));
    givenFailedCheck(
        "38888888888",
        RISK_LEVEL,
        Map.of("abc", "123", "level", 1),
        TestClockHolder.now.minus(90, DAYS));

    riskLevelService.writeThirdPillarRiskLevels(thirdPillarRun);

    verify(amlCheckRepository, never()).save(any());
    assertThat(createdCheckEvents()).isEmpty();
    assertJobRun("III pillar", 1, 0, 0);
  }

  @Test
  void skipsAResultWhoseMetadataDiffersOnlyInVersion() {
    AssessedRiskLevels thirdPillarRun =
        todaysRun(new RiskLevelResult("38888888888", 1, Map.of("abc", "123", "version", "2.0")));
    givenFailedCheck(
        "38888888888",
        RISK_LEVEL,
        Map.of("abc", "123", "level", 1, "version", "1.0"),
        TestClockHolder.now.minus(90, DAYS));

    riskLevelService.writeThirdPillarRiskLevels(thirdPillarRun);

    verify(amlCheckRepository, never()).save(any());
    assertJobRun("III pillar", 1, 0, 0);
  }

  @Test
  void writesACheckWhenTheMetadataDiffersInMoreThanVersion() {
    AssessedRiskLevels thirdPillarRun =
        todaysRun(new RiskLevelResult("38888888888", 1, Map.of("abc", "456", "version", "2.0")));
    givenFailedCheck(
        "38888888888",
        RISK_LEVEL,
        Map.of("abc", "123", "level", 1, "version", "2.0"),
        TestClockHolder.now.minus(90, DAYS));

    riskLevelService.writeThirdPillarRiskLevels(thirdPillarRun);

    verify(amlCheckRepository)
        .save(
            failedCheck(
                "38888888888", RISK_LEVEL, Map.of("abc", "456", "version", "2.0", "level", 1)));
    assertJobRun("III pillar", 1, 0, 1);
  }

  @Test
  void reportsARunWithoutResults() {
    AssessedRiskLevels thirdPillarRun = todaysRun();

    riskLevelService.writeThirdPillarRiskLevels(thirdPillarRun);

    verify(amlCheckRepository, never()).save(any());
    assertThat(createdCheckEvents()).isEmpty();
    assertJobRun("III pillar", 0, 0, 0);
  }

  @Test
  void writesATkfRiskLevelCheckForATkfResult() {
    AssessedRiskLevels tkfRun =
        todaysRun(new RiskLevelResult("38501010001", 1, Map.of("level", 1, "total_points", 105)));

    riskLevelService.writeTkfRiskLevels(tkfRun);

    verify(amlCheckRepository)
        .save(failedCheck("38501010001", TKF_RISK_LEVEL, Map.of("level", 1, "total_points", 105)));
    assertJobRun("TKF", 1, 0, 1);
  }

  @Test
  void skipsATkfResultAlreadyWrittenWithinSixMonths() {
    AssessedRiskLevels tkfRun =
        todaysRun(new RiskLevelResult("38501010001", 1, Map.of("level", 1, "total_points", 105)));
    givenFailedCheck(
        "38501010001",
        TKF_RISK_LEVEL,
        Map.of("level", 1, "total_points", 105),
        TestClockHolder.now.minus(90, DAYS));

    riskLevelService.writeTkfRiskLevels(tkfRun);

    verify(amlCheckRepository, never()).save(any());
    assertJobRun("TKF", 1, 0, 0);
  }

  @Test
  void neverTouchesTheViews() {
    AssessedRiskLevels thirdPillarRun =
        todaysRun(new RiskLevelResult("38888888888", 1, Map.of("key", 999)));
    AssessedRiskLevels tkfRun =
        todaysRun(new RiskLevelResult("38888888888", 1, Map.of("key", 999)));

    riskLevelService.writeThirdPillarRiskLevels(thirdPillarRun);
    riskLevelService.writeTkfRiskLevels(tkfRun);

    verifyNoInteractions(amlRiskReader, tkfRiskReader);
  }

  private static AssessedRiskLevels todaysRun(RiskLevelResult... results) {
    return new AssessedRiskLevels(TODAYS_RUN, List.of(results));
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
