package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_COMPLETED_CHILD;
import static ee.tuleva.onboarding.notification.email.EmailType.SAVINGS_FUND_ONBOARDING_COMPLETED_PERSON;

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
class OpenedAccountRepository {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  List<OpenedAccount> fetch(Instant openedFrom, Instant openedUntil) {
    return jdbcClient
        .sql(
            """
            SELECT onboarding.code AS code,
                   holder.first_name AS first_name,
                   holder.last_name AS last_name,
                   holder.email AS email,
                   EXISTS (SELECT 1
                           FROM unit_owner
                           WHERE unit_owner.personal_id = onboarding.code
                             AND unit_owner.language_preference = 'ENG'
                             AND unit_owner.snapshot_date =
                                 (SELECT MAX(snapshot_date) FROM unit_owner)) AS prefers_english,
                   EXISTS (SELECT 1
                           FROM parent_child_link link
                           WHERE link.child_personal_code = onboarding.code
                             AND link.status = 'ACTIVE'
                             AND link.suspended_at IS NULL
                             AND link.valid_until > :today) AS represented,
                   EXISTS (SELECT 1
                           FROM saving_fund_payment payment
                           WHERE payment.party_type = 'PERSON'
                             AND payment.party_code = onboarding.code
                             AND payment.status IN ('RECEIVED', 'VERIFIED', 'RESERVED',
                                                    'ISSUED', 'PROCESSED', 'FROZEN')) AS paid
            FROM savings_fund_onboarding onboarding
            JOIN users holder ON holder.personal_code = onboarding.code
            WHERE onboarding.type = 'PERSON'
              AND onboarding.status = 'COMPLETED'
              AND onboarding.updated_at >= :openedFrom
              AND onboarding.updated_at < :openedUntil
              AND NOT EXISTS (SELECT 1
                              FROM email
                              WHERE email.personal_code = onboarding.code
                                AND email.type IN (:welcomeEmailTypes))
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
            """)
        .param("openedFrom", Timestamp.from(openedFrom))
        .param("openedUntil", Timestamp.from(openedUntil))
        .param("today", LocalDate.now(clock))
        .param(
            "welcomeEmailTypes",
            List.of(
                SAVINGS_FUND_ONBOARDING_COMPLETED_CHILD.name(),
                SAVINGS_FUND_ONBOARDING_COMPLETED_PERSON.name()))
        .query(this::openedAccount)
        .list();
  }

  boolean prefersEnglish(String personalCode) {
    return jdbcClient
        .sql(
            """
            SELECT EXISTS (SELECT 1
                           FROM unit_owner
                           WHERE unit_owner.personal_id = :personalCode
                             AND unit_owner.language_preference = 'ENG'
                             AND unit_owner.snapshot_date =
                                 (SELECT MAX(snapshot_date) FROM unit_owner))
            """)
        .param("personalCode", personalCode)
        .query(Boolean.class)
        .single();
  }

  private OpenedAccount openedAccount(ResultSet rs, int rowNum) throws SQLException {
    return new OpenedAccount(
        rs.getString("code"),
        rs.getString("first_name"),
        rs.getString("last_name"),
        rs.getString("email"),
        rs.getBoolean("prefers_english"),
        rs.getBoolean("represented"),
        rs.getBoolean("paid"));
  }
}
