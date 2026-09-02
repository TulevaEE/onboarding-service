package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aCallback;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aDeviceLinkSession;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aNotificationSession;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aSessionSecret;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.sessionSecretDigest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SmartIdSessionTest {

  private final Instant now = Instant.parse("2026-09-02T10:00:00Z");

  @Test
  void acceptCallbackStoresTheUserChallengeVerifier() {
    SmartIdSession session = aDeviceLinkSession(now);

    session.acceptCallback(aCallback());

    assertThat(session.getUserChallengeVerifier()).isEqualTo("user-challenge-verifier");
  }

  @Test
  void acceptCallbackAcceptsTheSameCallbackTwice() {
    SmartIdSession session = aDeviceLinkSession(now);
    session.acceptCallback(aCallback());

    session.acceptCallback(aCallback());

    assertThat(session.getUserChallengeVerifier()).isEqualTo("user-challenge-verifier");
  }

  @Test
  void acceptCallbackRejectsADifferentVerifierForTheSameSession() {
    SmartIdSession session = aDeviceLinkSession(now);
    session.acceptCallback(aCallback());

    assertThatThrownBy(
            () ->
                session.acceptCallback(
                    new SmartIdCallback(
                        SmartIdFixture.aCallbackToken,
                        sessionSecretDigest(aSessionSecret),
                        "another-verifier")))
        .isInstanceOf(SmartIdCallbackRejectedException.class);
  }

  @Test
  void acceptCallbackRejectsAWrongCallbackToken() {
    SmartIdSession session = aDeviceLinkSession(now);

    assertThatThrownBy(
            () ->
                session.acceptCallback(
                    new SmartIdCallback(
                        "wrong-token", sessionSecretDigest(aSessionSecret), "verifier")))
        .isInstanceOf(SmartIdCallbackRejectedException.class);
    assertThat(session.getUserChallengeVerifier()).isNull();
  }

  @Test
  void acceptCallbackRejectsAWrongSessionSecretDigest() {
    SmartIdSession session = aDeviceLinkSession(now);

    assertThatThrownBy(
            () ->
                session.acceptCallback(
                    new SmartIdCallback(SmartIdFixture.aCallbackToken, "wrong-digest", "verifier")))
        .isInstanceOf(SmartIdCallbackRejectedException.class);
    assertThat(session.getUserChallengeVerifier()).isNull();
  }

  @Test
  void acceptCallbackRejectsNotificationLogins() {
    SmartIdSession session = aNotificationSession(now);

    assertThatThrownBy(() -> session.acceptCallback(aCallback()))
        .isInstanceOf(SmartIdCallbackRejectedException.class);
  }
}
