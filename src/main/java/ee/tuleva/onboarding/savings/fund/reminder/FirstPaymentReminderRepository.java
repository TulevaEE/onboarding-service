package ee.tuleva.onboarding.savings.fund.reminder;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_FIRST_PAYMENT_REMINDER_CHILD;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_FIRST_PAYMENT_REMINDER_COMPANY;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON;
import static ee.tuleva.onboarding.party.ParentChildLinkStatus.ACTIVE;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import ee.tuleva.onboarding.party.ParentChildLinkService;
import ee.tuleva.onboarding.personalcode.PersonalCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
  private static final List<String> CHECKS_KEEPING_AN_ONBOARDED_COMPANY_FOR_MANUAL_REVIEW =
      List.of(
          "KYB_HIGH_RISK_NACE",
          "KYB_SOLE_MEMBER_OWNERSHIP",
          "KYB_DUAL_MEMBER_OWNERSHIP",
          "KYB_SINGLE_BOARD_MEMBER_OWNERSHIP");
  private static final String LANGUAGE_PREFERENCES =
      """
      latest_snapshot AS (
        SELECT MAX(snapshot_date) AS snapshot_date FROM unit_owner
      ),
      language_preferences AS (
        SELECT unit_owner.personal_id, unit_owner.language_preference
        FROM unit_owner
        JOIN latest_snapshot ON unit_owner.snapshot_date = latest_snapshot.snapshot_date
      )
      """;

  private final JdbcClient jdbcClient;
  private final Clock clock;
  private final ParentChildLinkService parentChildLinkService;

  List<FirstPaymentReminder> fetchForAdults(Instant openedFrom, Instant openedUntil) {
    return window(
            jdbcClient.sql(
                "WITH "
                    + LANGUAGE_PREFERENCES
                    + """
                    SELECT onboarding.code AS recipient_code,
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
                       guardian.personal_code AS guardian_code,
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
        .query(this::childAccountGuardian)
        .list()
        .stream()
        .flatMap(guardian -> childReminder(guardian).stream())
        .toList();
  }

  List<FirstPaymentReminder> fetchForCompanies(Instant openedFrom, Instant openedUntil) {
    return earliestOpenedCompanyOfEachApplicant(
        window(
                jdbcClient.sql(
                    "WITH "
                        + LANGUAGE_PREFERENCES
                        + """
                    ,
                    latest_surveys AS (
                      SELECT survey.registry_code, survey.user_id
                      FROM kyb_survey survey
                      WHERE NOT EXISTS (SELECT 1
                                        FROM kyb_survey later
                                        WHERE later.registry_code = survey.registry_code
                                          AND later.created_time > survey.created_time)
                    ),
                    latest_manual_review_checks AS (
                      SELECT review_check.company_id,
                             review_check.success,
                             review_check.created_time
                      FROM aml_check review_check
                      WHERE review_check.type IN (:manualReviewCheckTypes)
                        AND NOT EXISTS (SELECT 1
                                        FROM aml_check later
                                        WHERE later.company_id = review_check.company_id
                                          AND later.type = review_check.type
                                          AND later.created_time > review_check.created_time)
                    )
                    SELECT applicant.personal_code AS recipient_code,
                           applicant.first_name AS first_name,
                           applicant.last_name AS last_name,
                           applicant.email AS email,
                           language_preferences.language_preference AS language_preference,
                           company.id AS account_id
                    FROM savings_fund_onboarding onboarding
                    JOIN latest_surveys ON latest_surveys.registry_code = onboarding.code
                    JOIN users applicant ON applicant.id = latest_surveys.user_id
                    LEFT JOIN company ON company.registry_code = onboarding.code
                    LEFT JOIN language_preferences
                      ON language_preferences.personal_id = applicant.personal_code
                    WHERE onboarding.type = 'LEGAL_ENTITY'
                      AND onboarding.status = 'COMPLETED'
                      AND onboarding.updated_at >= :openedFrom
                      AND onboarding.updated_at < :openedUntil
                      AND applicant.email IS NOT NULL
                      AND NOT EXISTS (SELECT 1
                                      FROM saving_fund_payment payment
                                      WHERE payment.party_type = 'LEGAL_ENTITY'
                                        AND payment.party_code = onboarding.code)
                      AND NOT EXISTS (SELECT 1
                                      FROM saving_fund_payment payment
                                      WHERE payment.remitter_id_code = onboarding.code)
                      AND NOT EXISTS (SELECT 1
                                      FROM email
                                      WHERE email.personal_code = applicant.personal_code
                                        AND email.type = :emailType
                                        AND email.created_date >= onboarding.updated_at)
                      AND NOT EXISTS (SELECT 1
                                      FROM latest_manual_review_checks review
                                      WHERE review.company_id = company.id
                                        AND review.success = false
                                        AND review.created_time >= onboarding.updated_at)
                    ORDER BY applicant.personal_code, onboarding.updated_at
                    """),
                openedFrom,
                openedUntil)
            .param("emailType", SAVINGS_FUND_FIRST_PAYMENT_REMINDER_COMPANY.name())
            .param("manualReviewCheckTypes", CHECKS_KEEPING_AN_ONBOARDED_COMPANY_FOR_MANUAL_REVIEW)
            .query(this::companyReminder)
            .list());
  }

  private static List<FirstPaymentReminder> earliestOpenedCompanyOfEachApplicant(
      List<FirstPaymentReminder> remindersInOpeningOrder) {
    return List.copyOf(
        remindersInOpeningOrder.stream()
            .collect(
                toMap(
                    FirstPaymentReminder::accountCode,
                    identity(),
                    (earliest, later) -> earliest,
                    LinkedHashMap::new))
            .values());
  }

  private StatementSpec window(StatementSpec statement, Instant openedFrom, Instant openedUntil) {
    return statement
        .param("openedFrom", Timestamp.from(openedFrom))
        .param("openedUntil", Timestamp.from(openedUntil))
        .param("today", LocalDate.now(clock));
  }

  private FirstPaymentReminder adultReminder(ResultSet rs, int rowNum) throws SQLException {
    return new FirstPaymentReminder(
        rs.getString("recipient_code"),
        rs.getString("first_name"),
        rs.getString("last_name"),
        rs.getString("email"),
        localeOf(rs.getString("language_preference")),
        SAVINGS_FUND_FIRST_PAYMENT_REMINDER_PERSON);
  }

  private FirstPaymentReminder companyReminder(ResultSet rs, int rowNum) throws SQLException {
    return new FirstPaymentReminder(
        rs.getString("recipient_code"),
        rs.getString("first_name"),
        rs.getString("last_name"),
        rs.getString("email"),
        localeOf(rs.getString("language_preference")),
        SAVINGS_FUND_FIRST_PAYMENT_REMINDER_COMPANY,
        rs.getObject("account_id", UUID.class));
  }

  private ChildAccountGuardian childAccountGuardian(ResultSet rs, int rowNum) throws SQLException {
    return new ChildAccountGuardian(
        rs.getString("account_code"),
        rs.getString("account_holder_name"),
        rs.getString("guardian_code"),
        rs.getString("first_name"),
        rs.getString("last_name"),
        rs.getString("email"));
  }

  private Optional<FirstPaymentReminder> childReminder(ChildAccountGuardian guardian) {
    return parentChildLinkService
        .findRepresentation(guardian.personalCode(), guardian.accountCode(), Set.of(ACTIVE))
        .map(
            accountId ->
                new FirstPaymentReminder(
                    guardian.accountCode(),
                    guardian.firstName(),
                    guardian.lastName(),
                    guardian.email(),
                    ESTONIAN,
                    SAVINGS_FUND_FIRST_PAYMENT_REMINDER_CHILD,
                    guardian.accountHolderName(),
                    accountId));
  }

  private boolean isAdult(FirstPaymentReminder reminder) {
    return !PersonalCode.isMinor(reminder.accountCode(), LocalDate.now(clock));
  }

  private Locale localeOf(@Nullable String languagePreference) {
    return ENGLISH_PREFERENCE.equals(languagePreference) ? Locale.ENGLISH : ESTONIAN;
  }

  private record ChildAccountGuardian(
      String accountCode,
      @Nullable String accountHolderName,
      String personalCode,
      String firstName,
      String lastName,
      String email) {}
}
