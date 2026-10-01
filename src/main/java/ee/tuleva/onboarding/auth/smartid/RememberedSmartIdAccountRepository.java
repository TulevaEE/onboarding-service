package ee.tuleva.onboarding.auth.smartid;

import ee.tuleva.onboarding.auth.browser.ExpiringRememberedEntries;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
class RememberedSmartIdAccountRepository implements ExpiringRememberedEntries {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  Optional<VerifiedSmartIdAccount> findUnexpired(long browserId) {
    return jdbcClient
        .sql(
            """
            SELECT personal_code, document_number, first_name, last_name, verified_at
            FROM remembered_smart_id_account
            WHERE browser_id = :browserId AND expires_at > :now
            """)
        .param("browserId", browserId)
        .param("now", Timestamp.from(Instant.now(clock)))
        .query(
            (rs, rowNum) ->
                new VerifiedSmartIdAccount(
                    rs.getString("personal_code"),
                    rs.getString("document_number"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getTimestamp("verified_at").toInstant()))
        .optional();
  }

  @Transactional
  void replace(long browserId, VerifiedSmartIdAccount account, Instant expiresAt) {
    lockBrowser(browserId);
    jdbcClient
        .sql("DELETE FROM remembered_smart_id_account WHERE browser_id = :browserId")
        .param("browserId", browserId)
        .update();
    jdbcClient
        .sql(
            """
            INSERT INTO remembered_smart_id_account
              (browser_id, personal_code, document_number, first_name, last_name,
               verified_at, expires_at)
            VALUES (:browserId, :personalCode, :documentNumber, :firstName, :lastName,
                    :verifiedAt, :expiresAt)
            """)
        .param("browserId", browserId)
        .param("personalCode", account.personalCode())
        .param("documentNumber", account.documentNumber())
        .param("firstName", account.firstName())
        .param("lastName", account.lastName())
        .param("verifiedAt", Timestamp.from(account.verifiedAt()))
        .param("expiresAt", Timestamp.from(expiresAt))
        .update();
  }

  private void lockBrowser(long browserId) {
    jdbcClient
        .sql("SELECT id FROM remembered_browser WHERE id = :browserId FOR UPDATE")
        .param("browserId", browserId)
        .query(Long.class)
        .optional();
  }

  void remove(long browserId) {
    jdbcClient
        .sql("DELETE FROM remembered_smart_id_account WHERE browser_id = :browserId")
        .param("browserId", browserId)
        .update();
  }

  int removeAllOf(String personalCode) {
    return jdbcClient
        .sql("DELETE FROM remembered_smart_id_account WHERE personal_code = :personalCode")
        .param("personalCode", personalCode)
        .update();
  }

  @Override
  public int removeExpired() {
    return jdbcClient
        .sql("DELETE FROM remembered_smart_id_account WHERE expires_at <= :now")
        .param("now", Timestamp.from(Instant.now(clock)))
        .update();
  }
}
