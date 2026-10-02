package ee.tuleva.onboarding.auth.mobileid;

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
class RememberedMobileIdPhoneRepository implements ExpiringRememberedEntries {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  Optional<RememberedMobileIdPhone> findUnexpired(long browserId, String personalCode) {
    return jdbcClient
        .sql(
            """
            SELECT id, phone_number FROM remembered_mobile_id_phone
            WHERE browser_id = :browserId AND personal_code = :personalCode
              AND expires_at > :now
            """)
        .param("browserId", browserId)
        .param("personalCode", personalCode)
        .param("now", Timestamp.from(Instant.now(clock)))
        .query(
            (rs, rowNum) ->
                new RememberedMobileIdPhone(rs.getLong("id"), rs.getString("phone_number")))
        .optional();
  }

  Optional<RememberedMobileIdPerson> findMostRecentUnexpired(long browserId) {
    return jdbcClient
        .sql(
            """
            SELECT id, personal_code, phone_number, first_name FROM remembered_mobile_id_phone
            WHERE browser_id = :browserId AND expires_at > :now AND first_name IS NOT NULL
            ORDER BY expires_at DESC, id DESC
            FETCH FIRST 1 ROW ONLY
            """)
        .param("browserId", browserId)
        .param("now", Timestamp.from(Instant.now(clock)))
        .query(
            (rs, rowNum) ->
                new RememberedMobileIdPerson(
                    rs.getString("personal_code"),
                    rs.getString("first_name"),
                    new RememberedMobileIdPhone(rs.getLong("id"), rs.getString("phone_number"))))
        .optional();
  }

  @Transactional
  void save(
      long browserId,
      String personalCode,
      String phoneNumber,
      String firstName,
      Instant expiresAt) {
    lockBrowser(browserId);
    int updated =
        jdbcClient
            .sql(
                """
                UPDATE remembered_mobile_id_phone
                SET phone_number = :phoneNumber, first_name = :firstName, expires_at = :expiresAt
                WHERE browser_id = :browserId AND personal_code = :personalCode
                """)
            .param("browserId", browserId)
            .param("personalCode", personalCode)
            .param("phoneNumber", phoneNumber)
            .param("firstName", firstName)
            .param("expiresAt", Timestamp.from(expiresAt))
            .update();
    if (updated == 0) {
      jdbcClient
          .sql(
              """
              INSERT INTO remembered_mobile_id_phone
                (browser_id, personal_code, phone_number, first_name, expires_at)
              VALUES (:browserId, :personalCode, :phoneNumber, :firstName, :expiresAt)
              """)
          .param("browserId", browserId)
          .param("personalCode", personalCode)
          .param("phoneNumber", phoneNumber)
          .param("firstName", firstName)
          .param("expiresAt", Timestamp.from(expiresAt))
          .update();
    }
  }

  private void lockBrowser(long browserId) {
    jdbcClient
        .sql("SELECT id FROM remembered_browser WHERE id = :browserId FOR UPDATE")
        .param("browserId", browserId)
        .query(Long.class)
        .optional();
  }

  void remove(long id) {
    jdbcClient
        .sql("DELETE FROM remembered_mobile_id_phone WHERE id = :id")
        .param("id", id)
        .update();
  }

  @Override
  public int removeExpired() {
    return jdbcClient
        .sql("DELETE FROM remembered_mobile_id_phone WHERE expires_at <= :now")
        .param("now", Timestamp.from(Instant.now(clock)))
        .update();
  }
}
