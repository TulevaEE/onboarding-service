package ee.tuleva.onboarding.oauth.server;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class GrantLifetimeTest {

  private static final Instant GRANTED = Instant.parse("2026-10-08T09:00:00Z");

  @Test
  void aGrantExpiresOneHundredEightyDaysAfterItWasGiven() {
    assertThat(GrantLifetime.expiresAt(GRANTED)).isEqualTo(GRANTED.plus(180, DAYS));
  }

  @Test
  void aGrantEndsThirtyDaysAfterItsLastRefreshWhenThatComesBeforeItsExpiry() {
    var expiresAt = GrantLifetime.expiresAt(GRANTED);

    assertThat(GrantLifetime.endsAt(expiresAt, GRANTED.plus(100, DAYS)))
        .isEqualTo(GRANTED.plus(130, DAYS));
  }

  @Test
  void aGrantEndsAtItsExpiryWhenItWasRefreshedWithinThirtyDaysOfIt() {
    var expiresAt = GrantLifetime.expiresAt(GRANTED);

    assertThat(GrantLifetime.endsAt(expiresAt, GRANTED.plus(170, DAYS))).isEqualTo(expiresAt);
  }

  @Test
  void aGrantRefreshedWithinThirtyDaysAndBeforeItsExpiryIsLive() {
    var expiresAt = GrantLifetime.expiresAt(GRANTED);

    assertThat(GrantLifetime.isLive(expiresAt, GRANTED.plus(150, DAYS), GRANTED.plus(179, DAYS)))
        .isTrue();
  }

  @Test
  void aGrantNotRefreshedForThirtyDaysIsNotLive() {
    var expiresAt = GrantLifetime.expiresAt(GRANTED);

    assertThat(GrantLifetime.isLive(expiresAt, GRANTED, GRANTED.plus(30, DAYS))).isFalse();
  }

  @Test
  void aGrantPastItsExpiryIsNotLiveEvenWhenJustRefreshed() {
    var expiresAt = GrantLifetime.expiresAt(GRANTED);

    assertThat(GrantLifetime.isLive(expiresAt, GRANTED.plus(179, DAYS), GRANTED.plus(180, DAYS)))
        .isFalse();
  }
}
