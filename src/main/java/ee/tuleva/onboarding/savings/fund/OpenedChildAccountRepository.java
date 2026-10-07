package ee.tuleva.onboarding.savings.fund;

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
class OpenedChildAccountRepository {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  List<OpenedChildAccount> fetch(Instant openedFrom, Instant openedUntil) {
    return jdbcClient
        .sql(
            """
            SELECT onboarding.code AS child_code,
                   child.first_name AS first_name,
                   child.last_name AS last_name,
                   EXISTS (SELECT 1
                           FROM saving_fund_payment payment
                           WHERE payment.party_type = 'PERSON'
                             AND payment.party_code = onboarding.code
                             AND payment.status IN ('RECEIVED', 'VERIFIED', 'RESERVED',
                                                    'ISSUED', 'PROCESSED', 'FROZEN')) AS paid
            FROM savings_fund_onboarding onboarding
            JOIN users child ON child.personal_code = onboarding.code
            WHERE onboarding.type = 'PERSON'
              AND onboarding.status = 'COMPLETED'
              AND onboarding.updated_at >= :openedFrom
              AND onboarding.updated_at < :openedUntil
              AND EXISTS (SELECT 1
                          FROM parent_child_link link
                          WHERE link.child_personal_code = onboarding.code
                            AND link.status = 'ACTIVE'
                            AND link.suspended_at IS NULL
                            AND link.valid_until > :today)
              AND NOT EXISTS (SELECT 1
                              FROM child_account_opened_email_claim claim
                              WHERE claim.child_code = onboarding.code)
            ORDER BY onboarding.code
            """)
        .param("openedFrom", Timestamp.from(openedFrom))
        .param("openedUntil", Timestamp.from(openedUntil))
        .param("today", LocalDate.now(clock))
        .query(this::openedChildAccount)
        .list();
  }

  private OpenedChildAccount openedChildAccount(ResultSet rs, int rowNum) throws SQLException {
    return new OpenedChildAccount(
        rs.getString("child_code"),
        rs.getString("first_name"),
        rs.getString("last_name"),
        rs.getBoolean("paid"));
  }
}
