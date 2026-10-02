package ee.tuleva.onboarding.auth.browser;

import static ee.tuleva.onboarding.auth.browser.PushLogin.MOBILE_ID;
import static ee.tuleva.onboarding.auth.browser.PushLogin.SMART_ID_NOTIFICATION;
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
  private static final Instant LATER = NOW.plus(Duration.ofDays(80));
  private static final Duration PUSH_INTERVAL = Duration.ofSeconds(10);

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @Autowired private RememberedBrowsers browsers;
  @Autowired private JdbcClient jdbcClient;

  @Test
  void findsABrowserItRemembered() {
    long id = browsers.add("token-hash", LATER);

    assertThat(browsers.findUnexpired("token-hash")).contains(new RememberedBrowser(id, LATER));
  }

  @Test
  void doesNotFindAnUnknownToken() {
    assertThat(browsers.findUnexpired("never-seen")).isEmpty();
  }

  @Test
  void doesNotFindABrowserWhoseValidityHasRunOut() {
    browsers.add("token-hash", NOW.minusSeconds(1));

    assertThat(browsers.findUnexpired("token-hash")).isEmpty();
  }

  @Test
  void rotatingKeepsTheBrowserButOnlyTheNewTokenFindsIt() {
    long id = browsers.add("old-token-hash", LATER);

    assertThat(browsers.rotate(id, "old-token-hash", "new-token-hash")).isTrue();

    assertThat(browsers.findUnexpired("old-token-hash")).isEmpty();
    assertThat(browsers.findUnexpired("new-token-hash")).contains(new RememberedBrowser(id, LATER));
  }

  @Test
  void aRotationThatAnotherLoginAlreadyMadeLeavesTheWinnersToken() {
    long id = browsers.add("old-token-hash", LATER);
    browsers.rotate(id, "old-token-hash", "winner-token-hash");

    assertThat(browsers.rotate(id, "old-token-hash", "loser-token-hash")).isFalse();

    assertThat(browsers.findUnexpired("winner-token-hash")).isPresent();
    assertThat(browsers.findUnexpired("loser-token-hash")).isEmpty();
  }

  @Test
  void extendingNeverShortensHowLongABrowserIsRemembered() {
    long id = browsers.add("token-hash", LATER);
    Instant longer = LATER.plus(Duration.ofDays(10));

    browsers.extendUntil(id, longer);
    browsers.extendUntil(id, LATER);

    assertThat(browsers.findUnexpired("token-hash")).contains(new RememberedBrowser(id, longer));
  }

  @Test
  void purgesOnlyBrowsersPastTheirValidity() {
    browsers.add("expired", NOW.minusSeconds(1));
    browsers.add("still-valid", LATER);

    assertThat(browsers.removeExpired()).isEqualTo(1);

    assertThat(browsers.findUnexpired("still-valid")).isPresent();
  }

  @Test
  void startsAPushLoginFromABrowserThatHasNotStartedOne() {
    long id = browsers.add("token-hash", LATER);

    assertThat(browsers.claimLoginStart(id, SMART_ID_NOTIFICATION, PUSH_INTERVAL)).isPresent();
  }

  @Test
  void refusesASecondPushLoginFromTheSameBrowserWithinTenSeconds() {
    long id = browsers.add("token-hash", LATER);
    browsers.claimLoginStart(id, SMART_ID_NOTIFICATION, PUSH_INTERVAL);

    assertThat(browsers.claimLoginStart(id, SMART_ID_NOTIFICATION, PUSH_INTERVAL)).isEmpty();
  }

  @Test
  void startsAPushLoginAgainOnceTenSecondsHavePassed() {
    long id = browsers.add("token-hash", LATER);
    previousPushLoginStartedAt(id, NOW.minus(PUSH_INTERVAL));

    assertThat(browsers.claimLoginStart(id, SMART_ID_NOTIFICATION, PUSH_INTERVAL)).isPresent();
  }

  @Test
  void stillRefusesAPushLoginStartedNineSecondsAgo() {
    long id = browsers.add("token-hash", LATER);
    previousPushLoginStartedAt(id, NOW.minusSeconds(9));

    assertThat(browsers.claimLoginStart(id, SMART_ID_NOTIFICATION, PUSH_INTERVAL)).isEmpty();
  }

  @Test
  void aPushLoginFromOneBrowserDoesNotHoldBackAnother() {
    long first = browsers.add("first", LATER);
    long second = browsers.add("second", LATER);
    browsers.claimLoginStart(first, SMART_ID_NOTIFICATION, PUSH_INTERVAL);

    assertThat(browsers.claimLoginStart(second, SMART_ID_NOTIFICATION, PUSH_INTERVAL)).isPresent();
  }

  @Test
  void aSmartIdPushDoesNotHoldBackAMobileIdLoginFromTheSameBrowser() {
    long id = browsers.add("token-hash", LATER);
    browsers.claimLoginStart(id, SMART_ID_NOTIFICATION, PUSH_INTERVAL);

    assertThat(browsers.claimLoginStart(id, MOBILE_ID, PUSH_INTERVAL)).isPresent();
    assertThat(browsers.claimLoginStart(id, MOBILE_ID, PUSH_INTERVAL)).isEmpty();
  }

  @Test
  void aReleasedPushLoginLetsTheNextOneStartRightAway() {
    long id = browsers.add("token-hash", LATER);
    Instant claimedAt =
        browsers.claimLoginStart(id, SMART_ID_NOTIFICATION, PUSH_INTERVAL).orElseThrow();

    browsers.releaseLoginStart(id, SMART_ID_NOTIFICATION, claimedAt);

    assertThat(browsers.claimLoginStart(id, SMART_ID_NOTIFICATION, PUSH_INTERVAL)).isPresent();
  }

  @Test
  void anOlderPushLoginEndingLeavesTheClaimOfANewerOneInPlace() {
    long id = browsers.add("token-hash", LATER);
    Instant olderClaim = NOW.minus(PUSH_INTERVAL);
    previousPushLoginStartedAt(id, olderClaim);
    browsers.claimLoginStart(id, SMART_ID_NOTIFICATION, PUSH_INTERVAL).orElseThrow();

    browsers.releaseLoginStart(id, SMART_ID_NOTIFICATION, olderClaim);

    assertThat(browsers.claimLoginStart(id, SMART_ID_NOTIFICATION, PUSH_INTERVAL)).isEmpty();
  }

  @Test
  void releasingASmartIdPushLeavesAMobileIdLoginFromTheSameBrowserClaimed() {
    long id = browsers.add("token-hash", LATER);
    Instant claimedAt =
        browsers.claimLoginStart(id, SMART_ID_NOTIFICATION, PUSH_INTERVAL).orElseThrow();
    browsers.claimLoginStart(id, MOBILE_ID, PUSH_INTERVAL);

    browsers.releaseLoginStart(id, SMART_ID_NOTIFICATION, claimedAt);

    assertThat(browsers.claimLoginStart(id, MOBILE_ID, PUSH_INTERVAL)).isEmpty();
  }

  @Test
  void releasingAPushLoginFromOneBrowserLeavesAnotherClaimed() {
    long first = browsers.add("first", LATER);
    long second = browsers.add("second", LATER);
    Instant claimedAt =
        browsers.claimLoginStart(first, SMART_ID_NOTIFICATION, PUSH_INTERVAL).orElseThrow();
    browsers.claimLoginStart(second, SMART_ID_NOTIFICATION, PUSH_INTERVAL);

    browsers.releaseLoginStart(first, SMART_ID_NOTIFICATION, claimedAt);

    assertThat(browsers.claimLoginStart(second, SMART_ID_NOTIFICATION, PUSH_INTERVAL)).isEmpty();
  }

  @Test
  void refusesAPushLoginFromABrowserWhoseValidityHasRunOut() {
    long id = browsers.add("token-hash", NOW.minusSeconds(1));

    assertThat(browsers.claimLoginStart(id, SMART_ID_NOTIFICATION, PUSH_INTERVAL)).isEmpty();
  }

  private void previousPushLoginStartedAt(long id, Instant startedAt) {
    jdbcClient
        .sql(
            "UPDATE remembered_browser SET smart_id_notification_login_started_at = :startedAt"
                + " WHERE id = :id")
        .param("startedAt", Timestamp.from(startedAt))
        .param("id", id)
        .update();
  }
}
