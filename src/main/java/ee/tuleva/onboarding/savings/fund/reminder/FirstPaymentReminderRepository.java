package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class FirstPaymentReminderRepository {

  private static final Locale ESTONIAN = Locale.of("et");
  private static final String ENGLISH_PREFERENCE = "ENG";

  private final JdbcClient jdbcClient;

  /**
   * Savers whose own savings fund account was opened within the given window, who have never sent
   * us a payment and who have not been reminded before. Children and companies are left out: they
   * have their own reminders.
   */
  List<FirstPaymentReminder> fetch(Instant openedFrom, Instant openedUntil) {
    return jdbcClient
        .sql(
            """
            WITH latest_snapshot AS (
              SELECT MAX(snapshot_date) AS snapshot_date FROM unit_owner
            ),
            language_preferences AS (
              SELECT unit_owner.personal_id, unit_owner.language_preference
              FROM unit_owner
              JOIN latest_snapshot ON unit_owner.snapshot_date = latest_snapshot.snapshot_date
            )
            SELECT onboarding.code AS personal_code,
                   users.first_name AS first_name,
                   users.last_name AS last_name,
                   users.email AS email,
                   language_preferences.language_preference AS language_preference
            FROM savings_fund_onboarding onboarding
            JOIN users ON users.personal_code = onboarding.code
            LEFT JOIN language_preferences ON language_preferences.personal_id = onboarding.code
            WHERE onboarding.type = 'PERSON'
              AND onboarding.status = 'COMPLETED'
              AND onboarding.status_changed_at >= :openedFrom
              AND onboarding.status_changed_at < :openedUntil
              AND users.email IS NOT NULL
              AND NOT EXISTS (SELECT 1
                              FROM saving_fund_payment payment
                              WHERE payment.party_type = 'PERSON'
                                AND payment.party_code = onboarding.code)
              AND NOT EXISTS (SELECT 1
                              FROM email
                              WHERE email.personal_code = onboarding.code
                                AND email.type = :emailType)
              AND NOT EXISTS (SELECT 1
                              FROM parent_child_link link
                              WHERE link.child_personal_code = onboarding.code)
            ORDER BY onboarding.code
            """)
        .param("openedFrom", Timestamp.from(openedFrom))
        .param("openedUntil", Timestamp.from(openedUntil))
        .param("emailType", SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON.name())
        .query(
            (rs, rowNum) ->
                new FirstPaymentReminder(
                    rs.getString("personal_code"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("email"),
                    localeOf(rs.getString("language_preference"))))
        .list();
  }

  private Locale localeOf(String languagePreference) {
    return ENGLISH_PREFERENCE.equals(languagePreference) ? Locale.ENGLISH : ESTONIAN;
  }
}
