package ee.tuleva.onboarding.aml.health;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.config.TestSchedulerLockConfiguration;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestSchedulerLockConfiguration.class)
class AmlHealthIntegrationTest {

  @Autowired private AmlCheckHealthRepository amlCheckHealthRepository;

  @Autowired private AmlHealthThresholdCache amlHealthThresholdCache;

  @Autowired private AmlHealthCheckService amlHealthCheckService;

  private final Instant NOW_INSTANT = Instant.parse("2025-05-16T12:00:00.00Z");
  private final Clock FIXED_CLOCK = Clock.fixed(NOW_INSTANT, ZoneId.of("UTC"));

  private static final String PERSONAL_CODE_1 = "37605030299";
  private static final String PERSONAL_CODE_2 = "39107050268";
  private static final String PERSONAL_CODE_3 = "38812022762";

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(FIXED_CLOCK);
    amlHealthThresholdCache.updateThresholdsForTest(Map.of());
    amlCheckHealthRepository.deleteAll();
    amlCheckHealthRepository.flush();
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  private AmlCheck createAndSaveAmlCheck(
      AmlCheckType type, Instant createdTime, String personalCode) {
    Clock customClock = Clock.fixed(createdTime, ZoneId.of("UTC"));
    ClockHolder.setClock(customClock);

    AmlCheck check =
        AmlCheck.builder()
            .type(type)
            .personalCode(personalCode)
            .success(true)
            .metadata(Map.of("integrationTest", true))
            .build();
    AmlCheck saved = amlCheckHealthRepository.saveAndFlush(check);
    ClockHolder.setClock(FIXED_CLOCK);
    return saved;
  }

  @Test
  @DisplayName("Full flow: thresholds are loaded and service correctly identifies delayed check")
  @Transactional
  void fullFlow_thresholdsLoaded_delayIdentified() {
    // given
    AmlCheckType testType = AmlCheckType.DOCUMENT;
    Instant check1Time = NOW_INSTANT.minus(Duration.ofDays(10));
    Instant check2Time = check1Time.plus(Duration.ofHours(2));
    Instant check3Time = check2Time.plus(Duration.ofHours(5));

    createAndSaveAmlCheck(testType, check1Time, PERSONAL_CODE_1);
    createAndSaveAmlCheck(testType, check2Time, PERSONAL_CODE_2);
    AmlCheck lastCheck = createAndSaveAmlCheck(testType, check3Time, PERSONAL_CODE_3);

    // when
    amlHealthThresholdCache.refreshThresholds();

    // then
    Optional<Duration> thresholdOpt = amlHealthThresholdCache.getThreshold(testType.name());
    assertThat(thresholdOpt).isPresent();
    Duration baseThreshold = Duration.ofHours(5);
    assertThat(thresholdOpt.get()).isEqualTo(baseThreshold);

    // given
    Instant futureTime =
        lastCheck.getCreatedTime().plus(baseThreshold.multipliedBy(3)).plus(Duration.ofMinutes(1));
    ClockHolder.setClock(Clock.fixed(futureTime, ZoneId.of("UTC")));

    // when
    boolean isDelayed = amlHealthCheckService.isCheckTypeDelayed(testType);

    // then
    assertThat(isDelayed).isTrue();
  }

  @Test
  @DisplayName("Full flow: service correctly identifies non-delayed check")
  @Transactional
  void fullFlow_notDelayed() {
    // given
    AmlCheckType testType = AmlCheckType.CONTACT_DETAILS;
    Instant check1Time = NOW_INSTANT.minus(Duration.ofDays(5));
    Instant check2Time = check1Time.plus(Duration.ofHours(1));

    createAndSaveAmlCheck(testType, check1Time, PERSONAL_CODE_1);
    AmlCheck lastCheck = createAndSaveAmlCheck(testType, check2Time, PERSONAL_CODE_2);

    // when
    amlHealthThresholdCache.refreshThresholds();

    // then
    Optional<Duration> thresholdOpt = amlHealthThresholdCache.getThreshold(testType.name());
    assertThat(thresholdOpt).isPresent();
    Duration baseThreshold = Duration.ofHours(1);
    assertThat(thresholdOpt.get()).isEqualTo(baseThreshold);

    // given
    Instant futureTime =
        lastCheck.getCreatedTime().plus(baseThreshold).plus(Duration.ofMinutes(10));
    ClockHolder.setClock(Clock.fixed(futureTime, ZoneId.of("UTC")));

    // when
    boolean isDelayed = amlHealthCheckService.isCheckTypeDelayed(testType);

    // then
    assertThat(isDelayed).isFalse();
  }

  @Test
  @DisplayName("Full flow: check type with no threshold is not considered delayed")
  @Transactional
  void fullFlow_noThreshold_notDelayed() {
    // given
    AmlCheckType typeWithData = AmlCheckType.SANCTION;
    AmlCheckType typeWithoutThreshold = AmlCheckType.PENSION_REGISTRY_NAME;

    createAndSaveAmlCheck(typeWithData, NOW_INSTANT.minus(2, ChronoUnit.DAYS), PERSONAL_CODE_1);
    createAndSaveAmlCheck(typeWithData, NOW_INSTANT.minus(1, ChronoUnit.DAYS), PERSONAL_CODE_2);

    // when
    amlHealthThresholdCache.refreshThresholds();

    // then
    assertThat(amlHealthThresholdCache.getThreshold(typeWithData.name())).isPresent();
    assertThat(amlHealthThresholdCache.getThreshold(typeWithoutThreshold.name())).isEmpty();

    boolean isDelayed = amlHealthCheckService.isCheckTypeDelayed(typeWithoutThreshold);
    assertThat(isDelayed).isFalse();
  }

  @Test
  @DisplayName("Full flow: check type with threshold but never run is considered delayed")
  @Transactional
  void fullFlow_thresholdExists_neverRun_isDelayed() {
    // given
    AmlCheckType typeToHaveThreshold = AmlCheckType.RISK_LEVEL;
    AmlCheckType typeNeverRun = AmlCheckType.RESIDENCY_MANUAL;

    createAndSaveAmlCheck(
        typeToHaveThreshold, NOW_INSTANT.minus(Duration.ofHours(2)), PERSONAL_CODE_1);
    createAndSaveAmlCheck(
        typeToHaveThreshold, NOW_INSTANT.minus(Duration.ofHours(1)), PERSONAL_CODE_2);

    // when
    amlHealthThresholdCache.refreshThresholds();

    // then
    assertThat(amlHealthThresholdCache.getThreshold(typeToHaveThreshold.name())).isPresent();
    amlHealthThresholdCache.updateThresholdsForTest(
        Map.of(typeNeverRun.name(), Duration.ofHours(1)));
    assertThat(amlHealthThresholdCache.getThreshold(typeNeverRun.name())).isPresent();

    boolean isDelayed = amlHealthCheckService.isCheckTypeDelayed(typeNeverRun);
    assertThat(isDelayed).isTrue();
  }
}
