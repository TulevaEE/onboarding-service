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

  boolean hasAssignment(long userId, String nudgeKey, int seasonYear) {
    return jdbcClient
            .sql(
                """
            SELECT count(*) FROM nudge_exposure
            WHERE user_id = :userId AND nudge_key = :nudgeKey AND season_year = :seasonYear
            """)
            .param("userId", userId)
            .param("nudgeKey", nudgeKey)
            .param("seasonYear", seasonYear)
            .query(Long.class)
            .single()
        > 0;
  }

  boolean isDueForSecondShowing(
      long userId, String nudgeKey, int seasonYear, Instant firstShownBefore) {
    return jdbcClient
            .sql(
                """
            SELECT count(*) FROM nudge_exposure
            WHERE user_id = :userId AND nudge_key = :nudgeKey AND season_year = :seasonYear
              AND arm = 'TREATMENT' AND assigned_at < :firstShownBefore AND second_shown_at IS NULL
            """)
            .param("userId", userId)
            .param("nudgeKey", nudgeKey)
            .param("seasonYear", seasonYear)
            .param("firstShownBefore", Timestamp.from(firstShownBefore))
            .query(Long.class)
            .single()
        > 0;
  }

  @Transactional
  boolean recordSecondShowing(
      long userId, String nudgeKey, int seasonYear, Instant firstShownBefore, Instant shownAt) {
    return jdbcClient
            .sql(
                """
                UPDATE nudge_exposure SET second_shown_at = :shownAt
                WHERE user_id = :userId AND nudge_key = :nudgeKey AND season_year = :seasonYear
                  AND arm = 'TREATMENT' AND assigned_at < :firstShownBefore
                  AND second_shown_at IS NULL
                """)
            .param("shownAt", Timestamp.from(shownAt))
            .param("userId", userId)
            .param("nudgeKey", nudgeKey)
            .param("seasonYear", seasonYear)
            .param("firstShownBefore", Timestamp.from(firstShownBefore))
            .update()
        > 0;
  }

  @Transactional
  void recordDismissal(long userId, String nudgeKey, int seasonYear, Instant dismissedAt) {
    stampDismissal(
        """
        UPDATE nudge_exposure SET dismissed_at = :dismissedAt
        WHERE user_id = :userId AND nudge_key = :nudgeKey AND season_year = :seasonYear
          AND second_shown_at IS NULL AND dismissed_at IS NULL
        """,
        userId,
        nudgeKey,
        seasonYear,
        dismissedAt);
    stampDismissal(
        """
        UPDATE nudge_exposure SET second_dismissed_at = :dismissedAt
        WHERE user_id = :userId AND nudge_key = :nudgeKey AND season_year = :seasonYear
          AND second_shown_at IS NOT NULL AND second_dismissed_at IS NULL
        """,
        userId,
        nudgeKey,
        seasonYear,
        dismissedAt);
  }

  private void stampDismissal(
      String update, long userId, String nudgeKey, int seasonYear, Instant dismissedAt) {
    jdbcClient
        .sql(update)
        .param("dismissedAt", Timestamp.from(dismissedAt))
        .param("userId", userId)
        .param("nudgeKey", nudgeKey)
        .param("seasonYear", seasonYear)
        .update();
  }
}
