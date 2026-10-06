package ee.tuleva.onboarding.auth.smartid;

import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aCallback;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aDeviceLinkSession;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aNotificationSession;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.aSessionSecret;
import static ee.tuleva.onboarding.auth.smartid.SmartIdFixture.sessionSecretDigest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SmartIdSessionTest {

  private final Instant now = Instant.parse("2026-09-02T10:00:00Z");

  @Test
  void hasLivedADurationOnlyOnceThatMuchTimeHasPassedSinceItWasCreated() {
    SmartIdSession session = aDeviceLinkSession(now.minusSeconds(90));

    assertThat(session.hasLived(Duration.ofSeconds(89), now)).isTrue();
    assertThat(session.hasLived(Duration.ofSeconds(90), now)).isTrue();
    assertThat(session.hasLived(Duration.ofSeconds(91), now)).isFalse();
  }

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

  @Test
  void isRedeemableOnlyWithTheSecretItIssued() {
    SmartIdSession session = aDeviceLinkSession(now);
    String secret = session.issueRedemptionSecret();

    assertThat(session.isRedeemableWith(secret)).isTrue();
    assertThat(session.isRedeemableWith(aNotificationSession(now).issueRedemptionSecret()))
        .isFalse();
    assertThat(session.isRedeemableWith("not base64url!")).isFalse();
    assertThat(session.isRedeemableWith(null)).isFalse();
  }

  @Test
  void isNotRedeemableBeforeASecretIsIssued() {
    assertThat(aDeviceLinkSession(now).isRedeemableWith("A".repeat(43))).isFalse();
  }

  @Test
  void issuesA32ByteBase64UrlSecretThatDiffersEveryTime() {
    SmartIdSession session = aDeviceLinkSession(now);

    String first = session.issueRedemptionSecret();
    String second = session.issueRedemptionSecret();

    assertThat(first).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second);
    assertThat(session.isRedeemableWith(first)).isFalse();
  }
}
