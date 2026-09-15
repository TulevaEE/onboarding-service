package ee.tuleva.onboarding.nudge;

import static ee.tuleva.onboarding.nudge.ExperimentArm.CONTROL;
import static ee.tuleva.onboarding.nudge.ExperimentArm.TREATMENT;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class NudgeExposureRepositoryTest {

  private static final String KEY = NudgeKey.SECOND_PILLAR_PAYMENT_RATE.name();
  private static final Instant ASSIGNED_AT = Instant.parse("2026-09-15T06:00:00Z");

  @Autowired private NudgeExposureRepository repository;
  @Autowired private JdbcClient jdbcClient;

  private long userId;

  @BeforeEach
  void setUp() {
    userId = insertUser();
  }

  @Test
  void theFirstAssignmentOfASeasonIsRecordedAndEveryLaterOneIsRefused() {
    assertThat(repository.recordAssignment(userId, KEY, 2026, TREATMENT, ASSIGNED_AT)).isTrue();
    assertThat(repository.recordAssignment(userId, KEY, 2026, TREATMENT, ASSIGNED_AT)).isFalse();
    assertThat(repository.recordAssignment(userId, KEY, 2026, CONTROL, ASSIGNED_AT)).isFalse();
    assertThat(armOf(2026)).contains("TREATMENT");
  }

  @Test
  void anAssignmentIsVisibleForItsSeasonOnly() {
    assertThat(repository.hasAssignment(userId, KEY, 2026)).isFalse();
    repository.recordAssignment(userId, KEY, 2026, CONTROL, ASSIGNED_AT);

    assertThat(repository.hasAssignment(userId, KEY, 2026)).isTrue();
    assertThat(repository.hasAssignment(userId, KEY, 2027)).isFalse();
  }

  @Test
  void theNextSeasonIsANewOpportunity() {
    assertThat(repository.recordAssignment(userId, KEY, 2026, CONTROL, ASSIGNED_AT)).isTrue();
    assertThat(repository.recordAssignment(userId, KEY, 2027, TREATMENT, ASSIGNED_AT)).isTrue();
    assertThat(armOf(2026)).contains("CONTROL");
    assertThat(armOf(2027)).contains("TREATMENT");
  }

  @Test
  void aDismissalIsStampedOnceAndNeverOverwritten() {
    repository.recordAssignment(userId, KEY, 2026, TREATMENT, ASSIGNED_AT);
    Instant firstDismissal = Instant.parse("2026-09-15T07:00:00Z");
    Instant secondDismissal = Instant.parse("2026-09-15T08:00:00Z");

    repository.recordDismissal(userId, KEY, 2026, firstDismissal);
    repository.recordDismissal(userId, KEY, 2026, secondDismissal);

    assertThat(dismissedAt(2026)).contains(firstDismissal);
  }

  @Test
  void aDismissalWithoutAnAssignmentChangesNothing() {
    repository.recordDismissal(userId, KEY, 2026, Instant.parse("2026-09-15T07:00:00Z"));

    assertThat(armOf(2026)).isEmpty();
  }

  private Optional<String> armOf(int seasonYear) {
    return jdbcClient
        .sql(
            "SELECT arm FROM nudge_exposure WHERE user_id = :userId AND nudge_key = :key"
                + " AND season_year = :seasonYear")
        .param("userId", userId)
        .param("key", KEY)
        .param("seasonYear", seasonYear)
        .query(String.class)
        .optional();
  }

  private Optional<Instant> dismissedAt(int seasonYear) {
    return jdbcClient
        .sql(
            "SELECT dismissed_at FROM nudge_exposure WHERE user_id = :userId AND nudge_key = :key"
                + " AND season_year = :seasonYear")
        .param("userId", userId)
        .param("key", KEY)
        .param("seasonYear", seasonYear)
        .query(Timestamp.class)
        .optional()
        .map(Timestamp::toInstant);
  }

  private long insertUser() {
    String personalCode =
        "3888888" + UUID.randomUUID().toString().replaceAll("\\D", "").substring(0, 4);
    jdbcClient
        .sql(
            """
            INSERT INTO users (active, personal_code, first_name, last_name, email,
                               created_date, updated_date)
            VALUES (true, :personalCode, 'Nudge', 'Exposure', :email, :now, :now)
            """)
        .param("personalCode", personalCode)
        .param("email", personalCode + "@example.invalid")
        .param("now", Timestamp.from(ASSIGNED_AT))
        .update();
    return jdbcClient
        .sql("SELECT id FROM users WHERE personal_code = :personalCode")
        .param("personalCode", personalCode)
        .query(Long.class)
        .single();
  }
}
