package ee.tuleva.onboarding.auth.smartid;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RememberedBrowsers {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  public Optional<RememberedBrowser> findUnexpired(String tokenHash) {
    return jdbcClient
        .sql(
            """
            SELECT personal_code, document_number, first_name, last_name, verified_at
            FROM smart_id_remembered_browser
            WHERE token_hash = :tokenHash AND expires_at > :now
            """)
        .param("tokenHash", tokenHash)
        .param("now", Timestamp.from(Instant.now(clock)))
        .query(
            (rs, rowNum) ->
                new RememberedBrowser(
                    rs.getString("personal_code"),
                    rs.getString("document_number"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getTimestamp("verified_at").toInstant()))
        .optional();
  }

  public void add(String tokenHash, RememberedBrowser browser, Instant expiresAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO smart_id_remembered_browser
              (token_hash, personal_code, document_number, first_name, last_name,
               verified_at, expires_at)
            VALUES (:tokenHash, :personalCode, :documentNumber, :firstName, :lastName,
                    :verifiedAt, :expiresAt)
            """)
        .param("tokenHash", tokenHash)
        .param("personalCode", browser.personalCode())
        .param("documentNumber", browser.documentNumber())
        .param("firstName", browser.firstName())
        .param("lastName", browser.lastName())
        .param("verifiedAt", Timestamp.from(browser.verifiedAt()))
        .param("expiresAt", Timestamp.from(expiresAt))
        .update();
  }

  public void remove(String tokenHash) {
    jdbcClient
        .sql("DELETE FROM smart_id_remembered_browser WHERE token_hash = :tokenHash")
        .param("tokenHash", tokenHash)
        .update();
  }

  public int removeAllOf(String personalCode) {
    return jdbcClient
        .sql("DELETE FROM smart_id_remembered_browser WHERE personal_code = :personalCode")
        .param("personalCode", personalCode)
        .update();
  }

  public int removeExpired() {
    return jdbcClient
        .sql("DELETE FROM smart_id_remembered_browser WHERE expires_at <= :now")
        .param("now", Timestamp.from(Instant.now(clock)))
        .update();
  }
}
