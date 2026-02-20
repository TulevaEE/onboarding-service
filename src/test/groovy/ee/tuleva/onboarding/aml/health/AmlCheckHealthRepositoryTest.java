package ee.tuleva.onboarding.aml.health;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.time.ClockHolder;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class AmlCheckHealthRepositoryTest {

  @Autowired private AmlCheckHealthRepository repository;

  @Autowired private EntityManager entityManager;

  @Autowired private DataSource dataSource;

  private static final String PERSONAL_CODE_1 = "37605030299";
  private static final String PERSONAL_CODE_2 = "39107050268";
  private static final String PERSONAL_CODE_3 = "38812022762";

  private static final Instant NOW_INSTANT = Instant.parse("2025-05-16T12:00:00.00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(NOW_INSTANT, ZoneId.of("UTC"));

  private static final Instant ONE_MONTH_AGO_INSTANT = NOW_INSTANT.minus(30, ChronoUnit.DAYS);

  private static final String CREATE_PUBLIC_SCHEMA_IF_NOT_EXISTS =
      "CREATE SCHEMA IF NOT EXISTS public";
  private static final String CREATE_AML_CHECK_TABLE_H2 =
      """
      CREATE TABLE IF NOT EXISTS public.aml_check (
          id BIGSERIAL PRIMARY KEY,
          personal_code VARCHAR(255),
          type VARCHAR(255) NOT NULL,
          success BOOLEAN NOT NULL,
          metadata VARCHAR(4000),
          created_time TIMESTAMP WITH TIME ZONE NOT NULL
      );
      """;

  @BeforeAll
  static void setupDatabase(@Autowired DataSource ds) throws Exception {
    try (Connection conn = ds.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute(CREATE_PUBLIC_SCHEMA_IF_NOT_EXISTS);
      stmt.execute(CREATE_AML_CHECK_TABLE_H2);
    }
    ClockHolder.setClock(FIXED_CLOCK);
  }

  @AfterAll
  static void resetGlobalClock() {
    ClockHolder.setDefaultClock();
  }

  @BeforeEach
  void cleanUpDataAndResetClock() throws Exception {
    try (Connection conn = dataSource.getConnection();
        Statement stmt = conn.createStatement()) {
      stmt.execute("DELETE FROM public.aml_check;");
    }
    ClockHolder.setClock(FIXED_CLOCK);
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
            .metadata(Map.of("testKey", "testValue"))
            .build();
    AmlCheck savedCheck = repository.saveAndFlush(check);
    entityManager.clear();

    ClockHolder.setClock(FIXED_CLOCK);
    return savedCheck;
  }

  @Test
  @DisplayName("findLastCheckTimeByType returns empty Optional when no data for that type")
  void findLastCheckTimeByType_noDataForType() {
    // given
    createAndSaveAmlCheck(
        AmlCheckType.DOCUMENT, NOW_INSTANT.minus(Duration.ofDays(1)), PERSONAL_CODE_1);

    // when
    Optional<Instant> result =
        repository.findLastCheckTimeByType(AmlCheckType.CONTACT_DETAILS.name());

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("findLastCheckTimeByType returns the latest time for the specified type")
  void findLastCheckTimeByType_returnsLatestTime() {
    // given
    Instant time1 = NOW_INSTANT.minus(Duration.ofDays(2));
    Instant time2Latest = NOW_INSTANT.minus(Duration.ofDays(1));
    Instant time3OtherType = NOW_INSTANT.minus(Duration.ofHours(5));

    createAndSaveAmlCheck(AmlCheckType.CONTACT_DETAILS, time1, PERSONAL_CODE_1);
    createAndSaveAmlCheck(AmlCheckType.CONTACT_DETAILS, time2Latest, PERSONAL_CODE_2);
    createAndSaveAmlCheck(AmlCheckType.DOCUMENT, time3OtherType, PERSONAL_CODE_3);

    // when
    Optional<Instant> result =
        repository.findLastCheckTimeByType(AmlCheckType.CONTACT_DETAILS.name());

    // then
    assertThat(result).isPresent();
    assertThat(result.get().truncatedTo(ChronoUnit.SECONDS))
        .isEqualTo(time2Latest.truncatedTo(ChronoUnit.SECONDS));
  }

  @Test
  @DisplayName("findMaxIntervalSecondsPerTypeSince returns empty list when no data")
  void findMaxIntervalSecondsPerTypeSince_noData() {
    // when
    List<AmlCheckTypeHealthThreshold> results =
        repository.findMaxIntervalSecondsPerTypeSince(ONE_MONTH_AGO_INSTANT);

    // then
    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName(
      "findMaxIntervalSecondsPerTypeSince returns empty list if only one check per type within period")
  void findMaxIntervalSecondsPerTypeSince_oneCheckPerType() {
    // given
    createAndSaveAmlCheck(
        AmlCheckType.CONTACT_DETAILS,
        NOW_INSTANT.minus(Duration.ofDays(5)),
        PERSONAL_CODE_1); // Within 1 month
    createAndSaveAmlCheck(
        AmlCheckType.DOCUMENT,
        NOW_INSTANT.minus(Duration.ofDays(6)),
        PERSONAL_CODE_2); // Within 1 month

    // when
    List<AmlCheckTypeHealthThreshold> results =
        repository.findMaxIntervalSecondsPerTypeSince(ONE_MONTH_AGO_INSTANT);

    // then
    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("findMaxIntervalSecondsPerTypeSince calculates correct max interval for one type")
  void findMaxIntervalSecondsPerTypeSince_oneTypeMultipleChecks() {
    // given
    Instant baseTime = NOW_INSTANT.minus(Duration.ofDays(15)); // Well within 1 month
    createAndSaveAmlCheck(AmlCheckType.SANCTION, baseTime, PERSONAL_CODE_1);
    createAndSaveAmlCheck(
        AmlCheckType.SANCTION, baseTime.plus(Duration.ofHours(1)), PERSONAL_CODE_2);
    createAndSaveAmlCheck(
        AmlCheckType.SANCTION, baseTime.plus(Duration.ofHours(3)), PERSONAL_CODE_3);
    createAndSaveAmlCheck(
        AmlCheckType.SANCTION, baseTime.plus(Duration.ofHours(4)), PERSONAL_CODE_1);

    // when
    List<AmlCheckTypeHealthThreshold> results =
        repository.findMaxIntervalSecondsPerTypeSince(ONE_MONTH_AGO_INSTANT);

    // then
    assertThat(results).hasSize(1);
    AmlCheckTypeHealthThreshold sanctionResult = results.get(0);
    assertThat(sanctionResult.getType()).isEqualTo(AmlCheckType.SANCTION.name());
    assertThat(sanctionResult.getMaxIntervalSeconds()).isEqualTo(Duration.ofHours(2).getSeconds());
  }

  @Test
  @DisplayName("findMaxIntervalSecondsPerTypeSince calculates intervals for multiple types")
  void findMaxIntervalSecondsPerTypeSince_multipleTypes() {
    // given
    Instant baseTime = NOW_INSTANT.minus(Duration.ofDays(10));
    createAndSaveAmlCheck(AmlCheckType.RISK_LEVEL, baseTime, PERSONAL_CODE_1);
    createAndSaveAmlCheck(
        AmlCheckType.RISK_LEVEL, baseTime.plus(Duration.ofMinutes(30)), PERSONAL_CODE_2);
    createAndSaveAmlCheck(
        AmlCheckType.RISK_LEVEL, baseTime.plus(Duration.ofMinutes(90)), PERSONAL_CODE_3);

    createAndSaveAmlCheck(AmlCheckType.SK_NAME, baseTime.plus(Duration.ofDays(1)), PERSONAL_CODE_1);
    createAndSaveAmlCheck(
        AmlCheckType.SK_NAME,
        baseTime.plus(Duration.ofDays(1)).plus(Duration.ofHours(2)),
        PERSONAL_CODE_2);

    // when
    List<AmlCheckTypeHealthThreshold> results =
        repository.findMaxIntervalSecondsPerTypeSince(ONE_MONTH_AGO_INSTANT);

    // then
    assertThat(results).hasSize(2);
    Optional<AmlCheckTypeHealthThreshold> riskResultOpt =
        results.stream()
            .filter(r -> r.getType().equals(AmlCheckType.RISK_LEVEL.name()))
            .findFirst();
    Optional<AmlCheckTypeHealthThreshold> skResultOpt =
        results.stream().filter(r -> r.getType().equals(AmlCheckType.SK_NAME.name())).findFirst();

    assertThat(riskResultOpt).isPresent();
    assertThat(riskResultOpt.get().getMaxIntervalSeconds())
        .isEqualTo(Duration.ofMinutes(60).getSeconds());
    assertThat(skResultOpt).isPresent();
    assertThat(skResultOpt.get().getMaxIntervalSeconds())
        .isEqualTo(Duration.ofHours(2).getSeconds());
  }

  @Test
  @DisplayName(
      "findMaxIntervalSecondsPerTypeSince excludes data older than the 'sinceTime' parameter")
  void findMaxIntervalSecondsPerTypeSince_excludesOldData() {
    // given
    Instant veryOldTime =
        NOW_INSTANT.minus(Duration.ofDays(40)); // Older than ONE_MONTH_AGO_INSTANT
    Instant recentTime = NOW_INSTANT.minus(Duration.ofDays(5)); // Newer than ONE_MONTH_AGO_INSTANT

    createAndSaveAmlCheck(AmlCheckType.POLITICALLY_EXPOSED_PERSON, veryOldTime, PERSONAL_CODE_1);
    createAndSaveAmlCheck(
        AmlCheckType.POLITICALLY_EXPOSED_PERSON,
        veryOldTime.plus(Duration.ofHours(5)),
        PERSONAL_CODE_2);

    createAndSaveAmlCheck(AmlCheckType.RESIDENCY_AUTO, recentTime, PERSONAL_CODE_3);
    createAndSaveAmlCheck(
        AmlCheckType.RESIDENCY_AUTO, recentTime.plus(Duration.ofHours(2)), PERSONAL_CODE_1);

    // when
    List<AmlCheckTypeHealthThreshold> results =
        repository.findMaxIntervalSecondsPerTypeSince(ONE_MONTH_AGO_INSTANT);

    // then
    assertThat(results).hasSize(1);
    AmlCheckTypeHealthThreshold recentResult = results.get(0);
    assertThat(recentResult.getType()).isEqualTo(AmlCheckType.RESIDENCY_AUTO.name());
    assertThat(recentResult.getMaxIntervalSeconds()).isEqualTo(Duration.ofHours(2).getSeconds());
  }

  @Test
  @DisplayName(
      "findMaxIntervalSecondsPerTypeSince handles intervals correctly when one check is before 'sinceTime'")
  void findMaxIntervalSecondsPerTypeSince_boundaryCondition() {
    // given
    // Check 1 is before ONE_MONTH_AGO_INSTANT
    Instant check1Time = ONE_MONTH_AGO_INSTANT.minus(Duration.ofDays(2));
    // Check 2 is after ONE_MONTH_AGO_INSTANT
    Instant check2Time = ONE_MONTH_AGO_INSTANT.plus(Duration.ofDays(1));
    // Check 3 is after ONE_MONTH_AGO_INSTANT
    Instant check3Time = ONE_MONTH_AGO_INSTANT.plus(Duration.ofDays(3));

    createAndSaveAmlCheck(AmlCheckType.SANCTION_OVERRIDE, check1Time, PERSONAL_CODE_1);
    createAndSaveAmlCheck(AmlCheckType.SANCTION_OVERRIDE, check2Time, PERSONAL_CODE_2);
    createAndSaveAmlCheck(AmlCheckType.SANCTION_OVERRIDE, check3Time, PERSONAL_CODE_3);

    // when
    List<AmlCheckTypeHealthThreshold> results =
        repository.findMaxIntervalSecondsPerTypeSince(ONE_MONTH_AGO_INSTANT);

    // then
    assertThat(results).hasSize(1);
    AmlCheckTypeHealthThreshold result = results.get(0);
    assertThat(result.getType()).isEqualTo(AmlCheckType.SANCTION_OVERRIDE.name());
    assertThat(result.getMaxIntervalSeconds()).isEqualTo(Duration.ofDays(2).getSeconds());
  }
}
