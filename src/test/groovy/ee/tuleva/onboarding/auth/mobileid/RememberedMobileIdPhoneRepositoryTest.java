package ee.tuleva.onboarding.auth.mobileid;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;

@JdbcTest
@Import({
  RememberedMobileIdPhoneRepository.class,
  RememberedMobileIdPhoneRepositoryTest.FixedClockConfig.class
})
@Transactional
class RememberedMobileIdPhoneRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
  private static final Instant LATER = NOW.plus(Duration.ofDays(365));
  private static final String PERSONAL_CODE = "38888888888";
  private static final String OTHER_PERSONAL_CODE = "48888888888";

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired private RememberedMobileIdPhoneRepository phones;
  @Autowired private JdbcClient jdbcClient;

  record Row(String personalCode, String phoneNumber, Instant expiresAt) {}

  @Test
  void remembersSeveralPeopleOnOneBrowser() {
    long browser = aBrowser("browser");

    phones.save(browser, PERSONAL_CODE, "+37255555555", LATER);
    phones.save(browser, OTHER_PERSONAL_CODE, "+37251234567", LATER);

    assertThat(rowsOf(browser))
        .containsExactlyInAnyOrder(
            new Row(PERSONAL_CODE, "+37255555555", LATER),
            new Row(OTHER_PERSONAL_CODE, "+37251234567", LATER));
  }

  @Test
  void aLaterLoginReplacesThePhoneAndSlidesTheValidity() {
    long browser = aBrowser("browser");
    phones.save(browser, PERSONAL_CODE, "+37255555555", NOW.plus(Duration.ofDays(30)));

    phones.save(browser, PERSONAL_CODE, "+37251234567", LATER);

    assertThat(rowsOf(browser)).containsExactly(new Row(PERSONAL_CODE, "+37251234567", LATER));
  }

  @Test
  void findsThePhoneRememberedForAPersonOnABrowser() {
    long browser = aBrowser("browser");
    long otherBrowser = aBrowser("other-browser");
    phones.save(browser, PERSONAL_CODE, "+37255555555", LATER);
    phones.save(otherBrowser, OTHER_PERSONAL_CODE, "+37251234567", LATER);

    assertThat(phones.findUnexpired(browser, PERSONAL_CODE))
        .hasValueSatisfying(phone -> assertThat(phone.phoneNumber()).isEqualTo("+37255555555"));
    assertThat(phones.findUnexpired(browser, OTHER_PERSONAL_CODE)).isEmpty();
    assertThat(phones.findUnexpired(otherBrowser, PERSONAL_CODE)).isEmpty();
  }

  @Test
  void doesNotFindAPhoneWhoseValidityHasRunOut() {
    long browser = aBrowser("browser");
    phones.save(browser, PERSONAL_CODE, "+37255555555", NOW.minusSeconds(1));

    assertThat(phones.findUnexpired(browser, PERSONAL_CODE)).isEmpty();
  }

  @Test
  void forgetsOnePhoneAndLeavesTheOthersOnTheBrowser() {
    long browser = aBrowser("browser");
    phones.save(browser, PERSONAL_CODE, "+37255555555", LATER);
    phones.save(browser, OTHER_PERSONAL_CODE, "+37251234567", LATER);
    long id = phones.findUnexpired(browser, PERSONAL_CODE).orElseThrow().id();

    phones.remove(id);

    assertThat(rowsOf(browser))
        .containsExactly(new Row(OTHER_PERSONAL_CODE, "+37251234567", LATER));
  }

  @Test
  void purgesOnlyPhonesPastTheirValidity() {
    long browser = aBrowser("browser");
    phones.save(browser, PERSONAL_CODE, "+37255555555", NOW.minusSeconds(1));
    phones.save(browser, OTHER_PERSONAL_CODE, "+37251234567", LATER);

    assertThat(phones.removeExpired()).isEqualTo(1);

    assertThat(rowsOf(browser))
        .containsExactly(new Row(OTHER_PERSONAL_CODE, "+37251234567", LATER));
  }

  @Test
  void goesWithTheBrowserWhenTheBrowserIsErased() {
    long browser = aBrowser("browser");
    phones.save(browser, PERSONAL_CODE, "+37255555555", LATER);

    jdbcClient.sql("DELETE FROM remembered_browser WHERE id = :id").param("id", browser).update();

    assertThat(rowsOf(browser)).isEmpty();
  }

  private List<Row> rowsOf(long browserId) {
    return jdbcClient
        .sql(
            "SELECT personal_code, phone_number, expires_at FROM remembered_mobile_id_phone"
                + " WHERE browser_id = :browserId")
        .param("browserId", browserId)
        .query(
            (rs, rowNum) ->
                new Row(
                    rs.getString("personal_code"),
                    rs.getString("phone_number"),
                    rs.getTimestamp("expires_at").toInstant()))
        .list();
  }

  private long aBrowser(String tokenHash) {
    var keyHolder = new GeneratedKeyHolder();
    jdbcClient
        .sql("INSERT INTO remembered_browser (token_hash, expires_at) VALUES (:hash, :expiresAt)")
        .param("hash", tokenHash)
        .param("expiresAt", Timestamp.from(LATER))
        .update(keyHolder, "id");
    return Objects.requireNonNull(keyHolder.getKeyAs(Long.class));
  }
}
