package ee.tuleva.onboarding.auth.smartid;

import static java.nio.charset.StandardCharsets.UTF_8;

import ee.sk.smartid.rest.dao.SessionStatus;
import java.io.Serial;
import java.io.Serializable;
import java.security.MessageDigest;
import java.time.Instant;
import lombok.Data;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

@Data
public class SmartIdSession implements Serializable {

  @Serial private static final long serialVersionUID = 7445120994281540802L;

  private final Instant createdAt;
  @ToString.Exclude private final SmartIdLogin login;
  @ToString.Exclude private @Nullable SessionStatus finalStatus;
  @ToString.Exclude private @Nullable String userChallengeVerifier;
  @ToString.Exclude private @Nullable SmartIdPerson person;
  private @Nullable SmartIdLoginError error;

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
}
