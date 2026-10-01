package ee.tuleva.onboarding.auth.mobileid;

import ee.tuleva.onboarding.auth.browser.ExpiringRememberedEntries;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class RememberedMobileIdPhoneRepository implements ExpiringRememberedEntries {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  void save(long browserId, String personalCode, String phoneNumber, Instant expiresAt) {
    int updated =
        jdbcClient
            .sql(
                """
                UPDATE remembered_mobile_id_phone
                SET phone_number = :phoneNumber, expires_at = :expiresAt
                WHERE browser_id = :browserId AND personal_code = :personalCode
                """)
            .param("browserId", browserId)
            .param("personalCode", personalCode)
            .param("phoneNumber", phoneNumber)
            .param("expiresAt", Timestamp.from(expiresAt))
            .update();
    if (updated == 0) {
      jdbcClient
          .sql(
              """
              INSERT INTO remembered_mobile_id_phone
                (browser_id, personal_code, phone_number, expires_at)
              VALUES (:browserId, :personalCode, :phoneNumber, :expiresAt)
              """)
          .param("browserId", browserId)
          .param("personalCode", personalCode)
          .param("phoneNumber", phoneNumber)
          .param("expiresAt", Timestamp.from(expiresAt))
          .update();
    }
  }

  @Override
  public int removeExpired() {
    return jdbcClient
        .sql("DELETE FROM remembered_mobile_id_phone WHERE expires_at <= :now")
        .param("now", Timestamp.from(Instant.now(clock)))
        .update();
  }
}
