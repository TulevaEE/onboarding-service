package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_FIRST_PAYMENT_REMINDER_CHILD;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON;

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
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class FirstPaymentReminderRepository {

  private static final Locale ESTONIAN = Locale.of("et");
  private static final String ENGLISH_PREFERENCE = "ENG";

  private final JdbcClient jdbcClient;
  private final Clock clock;

  List<FirstPaymentReminder> fetchForAdults(Instant openedFrom, Instant openedUntil) {
    return window(
            jdbcClient.sql(
                """
                WITH latest_snapshot AS (
                  SELECT MAX(snapshot_date) AS snapshot_date FROM unit_owner
                ),
                language_preferences AS (
                  SELECT unit_owner.personal_id, unit_owner.language_preference
                  FROM unit_owner
                  JOIN latest_snapshot ON unit_owner.snapshot_date = latest_snapshot.snapshot_date
                )
                SELECT onboarding.code AS account_code,
                       users.first_name AS first_name,
                       users.last_name AS last_name,
                       users.email AS email,
                       language_preferences.language_preference AS language_preference
                FROM savings_fund_onboarding onboarding
                JOIN users ON users.personal_code = onboarding.code
                LEFT JOIN language_preferences
                  ON language_preferences.personal_id = onboarding.code
                WHERE onboarding.type = 'PERSON'
                  AND onboarding.status = 'COMPLETED'
                  AND onboarding.updated_at >= :openedFrom
                  AND onboarding.updated_at < :openedUntil
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
                                  WHERE link.child_personal_code = onboarding.code
                                    AND link.status = 'ACTIVE'
                                    AND link.suspended_at IS NULL
                                    AND link.valid_until > :today)
                  AND NOT EXISTS (SELECT 1
                                  FROM company_party board_membership
                                  JOIN company ON company.id = board_membership.company_id
                                  JOIN saving_fund_payment company_payment
                                    ON company_payment.party_type = 'LEGAL_ENTITY'
                                   AND company_payment.party_code = company.registry_code
                                  WHERE board_membership.party_type = 'PERSON'
                                    AND board_membership.relationship_type = 'BOARD_MEMBER'
                                    AND board_membership.party_code = onboarding.code)
                ORDER BY onboarding.code
                """),
            openedFrom,
            openedUntil)
        .param("emailType", SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON.name())
        .query(this::adultReminder)
        .list()
        .stream()
        .filter(this::isAdult)
        .toList();
  }

  List<FirstPaymentReminder> fetchForChildren(Instant openedFrom, Instant openedUntil) {
    return window(
            jdbcClient.sql(
                """
                SELECT child.code AS account_code,
                       child_user.first_name || ' ' || child_user.last_name AS account_holder_name,
                       guardian.first_name AS first_name,
                       guardian.last_name AS last_name,
                       guardian.email AS email
                FROM savings_fund_onboarding child
                JOIN (SELECT DISTINCT link.child_personal_code, link.parent_personal_code
                      FROM parent_child_link link
                      WHERE link.status = 'ACTIVE'
                        AND link.suspended_at IS NULL
                        AND link.valid_until > :today) guardianship
                  ON guardianship.child_personal_code = child.code
                JOIN users guardian ON guardian.personal_code = guardianship.parent_personal_code
                LEFT JOIN users child_user ON child_user.personal_code = child.code
                WHERE child.type = 'PERSON'
                  AND child.status = 'COMPLETED'
                  AND child.updated_at >= :openedFrom
                  AND child.updated_at < :openedUntil
                  AND guardian.email IS NOT NULL
                  AND NOT EXISTS (SELECT 1
                                  FROM saving_fund_payment payment
                                  WHERE payment.party_type = 'PERSON'
                                    AND payment.party_code = child.code)
                  AND NOT EXISTS (SELECT 1
                                  FROM email
                                  WHERE email.personal_code = child.code
                                    AND email.type = :emailType)
                ORDER BY child.code, guardian.personal_code
                """),
            openedFrom,
            openedUntil)
        .param("emailType", SAVINGS_FUND_FIRST_PAYMENT_REMINDER_CHILD.name())
        .query(this::childReminder)
        .list();
  }

  private StatementSpec window(StatementSpec statement, Instant openedFrom, Instant openedUntil) {
    return statement
        .param("openedFrom", Timestamp.from(openedFrom))
        .param("openedUntil", Timestamp.from(openedUntil))
        .param("today", LocalDate.now(clock));
  }

  private FirstPaymentReminder adultReminder(ResultSet rs, int rowNum) throws SQLException {
    return new FirstPaymentReminder(
        rs.getString("account_code"),
        rs.getString("first_name"),
        rs.getString("last_name"),
        rs.getString("email"),
        localeOf(rs.getString("language_preference")),
        SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON,
        null);
  }

  private FirstPaymentReminder childReminder(ResultSet rs, int rowNum) throws SQLException {
    return new FirstPaymentReminder(
        rs.getString("account_code"),
        rs.getString("first_name"),
        rs.getString("last_name"),
        rs.getString("email"),
        ESTONIAN,
        SAVINGS_FUND_FIRST_PAYMENT_REMINDER_CHILD,
        rs.getString("account_holder_name"));
  }

  private boolean isAdult(FirstPaymentReminder reminder) {
    return !PersonalCode.isMinor(reminder.accountCode(), LocalDate.now(clock));
  }

  private Locale localeOf(@Nullable String languagePreference) {
    return ENGLISH_PREFERENCE.equals(languagePreference) ? Locale.ENGLISH : ESTONIAN;
  }
}
