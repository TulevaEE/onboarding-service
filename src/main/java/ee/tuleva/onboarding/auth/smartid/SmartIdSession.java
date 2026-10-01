package ee.tuleva.onboarding.auth.smartid;

import static java.nio.charset.StandardCharsets.UTF_8;

import ee.sk.smartid.rest.dao.SessionStatus;
import java.io.Serial;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

@Data
public class SmartIdSession implements Serializable {

  @Serial private static final long serialVersionUID = 7445120994281540802L;

  private static final int REDEMPTION_SECRET_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final Instant createdAt;
  @ToString.Exclude private final SmartIdLogin login;
  @ToString.Exclude private @Nullable SessionStatus finalStatus;
  @ToString.Exclude private @Nullable String userChallengeVerifier;
  @ToString.Exclude private @Nullable SmartIdPerson person;
  private @Nullable SmartIdLoginError error;

  @ToString.Exclude
  @Setter(AccessLevel.NONE)
  private byte @Nullable [] redemptionSecretDigest;

  public String getSessionId() {
    return login.sessionId();
  }

  public void acceptCallback(SmartIdCallback callback) {
    if (!(login instanceof DeviceLinkLogin deviceLinkLogin)) {
      throw new SmartIdCallbackRejectedException("Callback for a login without a device link");
    }
    deviceLinkLogin.verify(callback);
    if (userChallengeVerifier != null
        && !MessageDigest.isEqual(
            userChallengeVerifier.getBytes(UTF_8),
            callback.userChallengeVerifier().getBytes(UTF_8))) {
      throw new SmartIdCallbackRejectedException("Callback already accepted with another verifier");
    }
    userChallengeVerifier = callback.userChallengeVerifier();
  }

  public String issueRedemptionSecret() {
    byte[] secret = new byte[REDEMPTION_SECRET_BYTES];
    RANDOM.nextBytes(secret);
    redemptionSecretDigest = sha256(secret);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
  }

  public boolean isRedeemableWith(@Nullable String redemptionSecret) {
    if (redemptionSecretDigest == null || redemptionSecret == null) {
      return false;
    }
    try {
      return MessageDigest.isEqual(
          redemptionSecretDigest, sha256(Base64.getUrlDecoder().decode(redemptionSecret)));
    } catch (IllegalArgumentException notBase64Url) {
      return false;
    }
  }

  private static byte[] sha256(byte[] bytes) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
