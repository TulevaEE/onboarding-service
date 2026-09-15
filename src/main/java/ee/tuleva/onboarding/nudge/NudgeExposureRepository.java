package ee.tuleva.onboarding.nudge;

import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
class NudgeExposureRepository {

  private final JdbcClient jdbcClient;

  @Transactional
  boolean recordAssignment(
      long userId, String nudgeKey, int seasonYear, ExperimentArm arm, Instant assignedAt) {
    return jdbcClient
            .sql(
                """
                INSERT INTO nudge_exposure (user_id, nudge_key, season_year, arm, assigned_at)
                VALUES (:userId, :nudgeKey, :seasonYear, :arm, :assignedAt)
                ON CONFLICT DO NOTHING
                """)
            .param("userId", userId)
            .param("nudgeKey", nudgeKey)
            .param("seasonYear", seasonYear)
            .param("arm", arm.name())
            .param("assignedAt", Timestamp.from(assignedAt))
            .update()
        > 0;
  }

  @Transactional
  void recordDismissal(long userId, String nudgeKey, int seasonYear, Instant dismissedAt) {
    jdbcClient
        .sql(
            """
            UPDATE nudge_exposure SET dismissed_at = :dismissedAt
            WHERE user_id = :userId AND nudge_key = :nudgeKey AND season_year = :seasonYear
              AND dismissed_at IS NULL
            """)
        .param("dismissedAt", Timestamp.from(dismissedAt))
        .param("userId", userId)
        .param("nudgeKey", nudgeKey)
        .param("seasonYear", seasonYear)
        .update();
  }
}
