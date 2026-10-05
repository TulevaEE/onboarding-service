package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_ABANDONMENT_CHILD;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ChildOnboardingAbandonmentReminderRepository {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  List<ChildOnboardingAbandonmentReminder> fetch(Instant startedFrom, Instant startedUntil) {
    return jdbcClient
        .sql(
            """
            WITH latest_snapshot AS (
              SELECT MAX(snapshot_date) AS snapshot_date FROM unit_owner
            ),
            english_speakers AS (
              SELECT unit_owner.personal_id
              FROM unit_owner
              JOIN latest_snapshot ON unit_owner.snapshot_date = latest_snapshot.snapshot_date
              WHERE unit_owner.language_preference = 'ENG'
            )
            SELECT DISTINCT parent.personal_code AS parent_code,
                            parent.first_name AS first_name,
                            parent.last_name AS last_name,
                            parent.email AS email
            FROM savings_fund_onboarding child
            JOIN parent_child_link link
              ON link.child_personal_code = child.code
             AND link.status = 'ACTIVE'
             AND link.suspended_at IS NULL
             AND link.valid_until > :today
            JOIN users parent ON parent.personal_code = link.parent_personal_code
            WHERE child.type = 'PERSON'
              AND child.status = 'PENDING'
              AND child.created_at >= :startedFrom
              AND child.created_at < :startedUntil
              AND parent.email IS NOT NULL
              AND NOT EXISTS (SELECT 1
                              FROM kyc_survey survey
                              JOIN users child_user ON child_user.id = survey.user_id
                              WHERE child_user.personal_code = child.code)
              AND NOT EXISTS (SELECT 1
                              FROM email
                              WHERE email.personal_code = parent.personal_code
                                AND email.type = :emailType)
              AND NOT EXISTS (SELECT 1
                              FROM english_speakers
                              WHERE english_speakers.personal_id = parent.personal_code)
            ORDER BY parent.personal_code
            """)
        .param("startedFrom", Timestamp.from(startedFrom))
        .param("startedUntil", Timestamp.from(startedUntil))
        .param("today", LocalDate.now(clock))
        .param("emailType", SAVINGS_FUND_ONBOARDING_ABANDONMENT_CHILD.name())
        .query(this::reminder)
        .list();
  }

  private ChildOnboardingAbandonmentReminder reminder(ResultSet rs, int rowNum)
      throws SQLException {
    return new ChildOnboardingAbandonmentReminder(
        rs.getString("parent_code"),
        rs.getString("first_name"),
        rs.getString("last_name"),
        rs.getString("email"));
  }
}
