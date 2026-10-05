package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_ABANDONMENT_CHILD;

import ee.tuleva.onboarding.auth.principal.PersonImpl;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ChildOnboardingAbandonmentReminderRepository {

  private static final Locale ESTONIAN = Locale.of("et");
  private static final String ENGLISH_PREFERENCE = "ENG";

  private final JdbcClient jdbcClient;
  private final Clock clock;

  List<ChildOnboardingAbandonmentReminder> fetch(Instant startedFrom, Instant startedUntil) {
    return jdbcClient
        .sql(
            """
            SELECT DISTINCT parent.id AS parent_id,
                            parent.personal_code AS parent_code,
                            parent.first_name AS first_name,
                            parent.last_name AS last_name,
                            parent.email AS email,
                            preference.language_preference AS language_preference
            FROM savings_fund_onboarding child
            JOIN parent_child_link link
              ON link.child_personal_code = child.code
             AND link.relationship_type = 'LEGAL_REPRESENTATIVE'
             AND link.status = 'ACTIVE'
             AND link.suspended_at IS NULL
             AND link.valid_until > :today
            JOIN users parent ON parent.personal_code = link.parent_personal_code
            LEFT JOIN unit_owner preference
              ON preference.personal_id = parent.personal_code
             AND preference.snapshot_date = (SELECT MAX(snapshot_date) FROM unit_owner)
            WHERE child.type = 'PERSON'
              AND child.status = 'PENDING'
              AND child.created_at >= :startedFrom
              AND child.created_at < :startedUntil
              AND TRIM(parent.email) <> ''
              AND NOT EXISTS (SELECT 1
                              FROM kyc_survey survey
                              JOIN users child_user ON child_user.id = survey.user_id
                              WHERE child_user.personal_code = child.code)
              AND NOT EXISTS (SELECT 1
                              FROM email
                              WHERE email.personal_code = parent.personal_code
                                AND email.type = :emailType
                                AND email.created_date >= child.created_at)
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
        rs.getLong("parent_id"),
        new PersonImpl(
            rs.getString("parent_code"), rs.getString("first_name"), rs.getString("last_name")),
        rs.getString("email"),
        localeOf(rs.getString("language_preference")));
  }

  private Locale localeOf(@Nullable String languagePreference) {
    return ENGLISH_PREFERENCE.equals(languagePreference) ? Locale.ENGLISH : ESTONIAN;
  }
}
