package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.rest.dao.NotificationAuthenticationSessionRequest;

public record NotificationLogin(
    String sessionId, NotificationAuthenticationSessionRequest request, String verificationCode)
    implements SmartIdLogin {

  @Override
  public String toString() {
    return "NotificationLogin[sessionId=" + sessionId + "]";
  }
}
