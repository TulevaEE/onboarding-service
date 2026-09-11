package ee.tuleva.onboarding.auth.smartid;

import static java.nio.charset.StandardCharsets.UTF_8;

import ee.sk.smartid.exception.SessionSecretMismatchException;
import ee.sk.smartid.exception.permanent.SmartIdClientException;
import ee.sk.smartid.rest.dao.DeviceLinkAuthenticationSessionRequest;
import ee.sk.smartid.util.CallbackUrlUtil;
import java.net.URI;
import java.security.MessageDigest;

public record DeviceLinkLogin(
    String sessionId,
    String sessionToken,
    String sessionSecret,
    URI deviceLinkBase,
    DeviceLinkAuthenticationSessionRequest request,
    String callbackToken,
    String initialCallbackUrl,
    String language)
    implements SmartIdLogin {

  public void verify(SmartIdCallback callback) {
    if (!MessageDigest.isEqual(callbackToken.getBytes(UTF_8), callback.value().getBytes(UTF_8))) {
      throw new SmartIdCallbackRejectedException("Callback token mismatch");
    }
    try {
      CallbackUrlUtil.validateSessionSecretDigest(callback.sessionSecretDigest(), sessionSecret);
    } catch (SessionSecretMismatchException | SmartIdClientException e) {
      throw new SmartIdCallbackRejectedException("Callback session secret digest mismatch");
    }
  }

  @Override
  public String toString() {
    return "DeviceLinkLogin[sessionId=" + sessionId + "]";
  }
}
