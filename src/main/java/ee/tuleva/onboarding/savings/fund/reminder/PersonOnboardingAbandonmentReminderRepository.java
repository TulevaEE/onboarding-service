package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_ABANDONMENT_PERSON;

import ee.tuleva.onboarding.auth.principal.PersonImpl;
import ee.tuleva.onboarding.personalcode.PersonalCode;
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
class PersonOnboardingAbandonmentReminderRepository {

  private static final String ENGLISH_PREFERENCE = "ENG";

  private final JdbcClient jdbcClient;
  private final Clock clock;

  List<PersonOnboardingAbandonmentReminder> fetch(Instant viewedFrom, Instant viewedUntil) {
    return jdbcClient
        .sql(
            """
            SELECT users.id AS user_id,
                   users.personal_code AS personal_code,
                   users.first_name AS first_name,
                   users.last_name AS last_name,
                   users.email AS email,
                   preference.language_preference AS language_preference
            FROM (SELECT event_log.principal AS personal_code,
                         MIN(event_log."timestamp") AS first_viewed_at,
                         MAX(event_log."timestamp") AS last_viewed_at
                  FROM event_log
                  WHERE event_log.type = 'PAGE_VIEW'
                    AND event_log.data #>> '{path}' = '/savings-fund/onboarding/person'
                    AND event_log."timestamp" >= :viewedFrom
                  GROUP BY event_log.principal) survey_view
            JOIN users ON users.personal_code = survey_view.personal_code
            LEFT JOIN unit_owner preference
              ON preference.personal_id = users.personal_code
             AND preference.snapshot_date = (SELECT MAX(snapshot_date) FROM unit_owner)
            WHERE survey_view.last_viewed_at < :viewedUntil
              AND TRIM(users.email) <> ''
              AND NOT EXISTS (SELECT 1
                              FROM savings_fund_onboarding onboarding
                              WHERE onboarding.code = users.personal_code
                                AND onboarding.type = 'PERSON')
              AND NOT EXISTS (SELECT 1
                              FROM kyc_survey survey
                              WHERE survey.user_id = users.id
                                AND COALESCE(survey.survey ->> 'purpose', 'PERSONAL_ONBOARDING')
                                    = 'PERSONAL_ONBOARDING')
              AND NOT EXISTS (SELECT 1
                              FROM parent_child_link link
                              WHERE link.child_personal_code = users.personal_code
                                AND link.status = 'ACTIVE'
                                AND link.suspended_at IS NULL
                                AND link.valid_until > :today)
              AND NOT EXISTS (SELECT 1
                              FROM parent_child_link link
                              JOIN savings_fund_onboarding child_onboarding
                                ON child_onboarding.code = link.child_personal_code
                               AND child_onboarding.type = 'PERSON'
                              WHERE link.parent_personal_code = users.personal_code
                                AND child_onboarding.updated_at >= survey_view.first_viewed_at)
              AND NOT EXISTS (SELECT 1
                              FROM email
                              WHERE email.personal_code = users.personal_code
                                AND email.type = :emailType)
            ORDER BY users.personal_code
            """)
        .param("viewedFrom", Timestamp.from(viewedFrom))
        .param("viewedUntil", Timestamp.from(viewedUntil))
        .param("today", LocalDate.now(clock))
        .param("emailType", SAVINGS_FUND_ONBOARDING_ABANDONMENT_PERSON.name())
        .query(this::reminder)
        .list()
        .stream()
        .filter(this::isAdult)
        .toList();
  }

  private PersonOnboardingAbandonmentReminder reminder(ResultSet rs, int rowNum)
      throws SQLException {
    return new PersonOnboardingAbandonmentReminder(
        rs.getLong("user_id"),
        new PersonImpl(
            rs.getString("personal_code"), rs.getString("first_name"), rs.getString("last_name")),
        rs.getString("email"),
        localeOf(rs.getString("language_preference")));
  }

  private boolean isAdult(PersonOnboardingAbandonmentReminder reminder) {
    return !PersonalCode.isMinor(reminder.person().getPersonalCode(), LocalDate.now(clock));
  }

  private Locale localeOf(@Nullable String languagePreference) {
    return ENGLISH_PREFERENCE.equals(languagePreference) ? Locale.ENGLISH : Locale.of("et");
  }
}
