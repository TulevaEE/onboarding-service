package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.documentNumber;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.firstName;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.lastName;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.personalCode;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@JdbcTest
@Import({RememberedBrowsers.class, RememberedBrowsersTest.FixedClockConfig.class})
@Transactional
class RememberedBrowsersTest {

  private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  private static final Duration PUSH_INTERVAL = Duration.ofSeconds(30);

  @Autowired private RememberedBrowsers browsers;
  @Autowired private JdbcClient jdbcClient;

  private RememberedBrowser aBrowser() {
    return new RememberedBrowser(
        personalCode, documentNumber, firstName, lastName, NOW.minus(Duration.ofDays(10)));
  }

  @Test
  void findsABrowserItRemembered() {
    browsers.add("token-hash", aBrowser(), NOW.plus(Duration.ofDays(80)));

    assertThat(browsers.findUnexpired("token-hash")).contains(aBrowser());
  }

  @Test
  void doesNotFindAnUnknownToken() {
    assertThat(browsers.findUnexpired("never-seen")).isEmpty();
  }

  @Test
  void doesNotFindABrowserWhoseValidityHasRunOut() {
    browsers.add("token-hash", aBrowser(), NOW.minusSeconds(1));

    assertThat(browsers.findUnexpired("token-hash")).isEmpty();
  }

  @Test
  void forgetsASingleBrowser() {
    browsers.add("first", aBrowser(), NOW.plus(Duration.ofDays(80)));
    browsers.add("second", aBrowser(), NOW.plus(Duration.ofDays(80)));

    browsers.remove("first");

    assertThat(browsers.findUnexpired("first")).isEmpty();
    assertThat(browsers.findUnexpired("second")).isPresent();
  }

  @Test
  void forgetsEveryBrowserOfOnePersonAndLeavesOthersAlone() {
    browsers.add("mine-one", aBrowser(), NOW.plus(Duration.ofDays(80)));
    browsers.add("mine-two", aBrowser(), NOW.plus(Duration.ofDays(80)));
    browsers.add(
        "somebody-else",
        new RememberedBrowser("38501010005", "PNOEE-38501010005-MOCK-Q", "Malle", "Mänd", NOW),
        NOW.plus(Duration.ofDays(80)));

    assertThat(browsers.removeAllOf(personalCode)).isEqualTo(2);

    assertThat(browsers.findUnexpired("mine-one")).isEmpty();
    assertThat(browsers.findUnexpired("mine-two")).isEmpty();
    assertThat(browsers.findUnexpired("somebody-else")).isPresent();
  }

  @Test
  void purgesOnlyBrowsersPastTheirValidity() {
    browsers.add("expired", aBrowser(), NOW.minusSeconds(1));
    browsers.add("still-valid", aBrowser(), NOW.plus(Duration.ofDays(80)));

    assertThat(browsers.removeExpired()).isEqualTo(1);

    assertThat(browsers.findUnexpired("still-valid")).isPresent();
  }

  @Test
  void startsAPushLoginFromABrowserThatHasNotStartedOne() {
    browsers.add("token-hash", aBrowser(), NOW.plus(Duration.ofDays(80)));

    assertThat(browsers.claimNotificationLoginStart("token-hash", PUSH_INTERVAL)).isTrue();
  }

  @Test
  void refusesASecondPushLoginFromTheSameBrowserWithinThirtySeconds() {
    browsers.add("token-hash", aBrowser(), NOW.plus(Duration.ofDays(80)));
    browsers.claimNotificationLoginStart("token-hash", PUSH_INTERVAL);

    assertThat(browsers.claimNotificationLoginStart("token-hash", PUSH_INTERVAL)).isFalse();
  }

  @Test
  void startsAPushLoginAgainOnceThirtySecondsHavePassed() {
    browsers.add("token-hash", aBrowser(), NOW.plus(Duration.ofDays(80)));
    previousPushLoginStartedAt("token-hash", NOW.minus(PUSH_INTERVAL));

    assertThat(browsers.claimNotificationLoginStart("token-hash", PUSH_INTERVAL)).isTrue();
  }

  @Test
  void stillRefusesAPushLoginStartedTwentyNineSecondsAgo() {
    browsers.add("token-hash", aBrowser(), NOW.plus(Duration.ofDays(80)));
    previousPushLoginStartedAt("token-hash", NOW.minusSeconds(29));

    assertThat(browsers.claimNotificationLoginStart("token-hash", PUSH_INTERVAL)).isFalse();
  }

  @Test
  void aPushLoginFromOneBrowserDoesNotHoldBackAnother() {
    browsers.add("first", aBrowser(), NOW.plus(Duration.ofDays(80)));
    browsers.add("second", aBrowser(), NOW.plus(Duration.ofDays(80)));
    browsers.claimNotificationLoginStart("first", PUSH_INTERVAL);

    assertThat(browsers.claimNotificationLoginStart("second", PUSH_INTERVAL)).isTrue();
  }

  @Test
  void refusesAPushLoginFromABrowserWhoseValidityHasRunOut() {
    browsers.add("token-hash", aBrowser(), NOW.minusSeconds(1));

    assertThat(browsers.claimNotificationLoginStart("token-hash", PUSH_INTERVAL)).isFalse();
  }

  private void previousPushLoginStartedAt(String tokenHash, Instant startedAt) {
    jdbcClient
        .sql(
            "UPDATE smart_id_remembered_browser SET notification_login_started_at = :startedAt"
                + " WHERE token_hash = :tokenHash")
        .param("startedAt", Timestamp.from(startedAt))
        .param("tokenHash", tokenHash)
        .update();
  }
}
