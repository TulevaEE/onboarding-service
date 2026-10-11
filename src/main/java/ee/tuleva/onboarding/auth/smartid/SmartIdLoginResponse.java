package ee.tuleva.onboarding.auth.smartid;

import org.jspecify.annotations.Nullable;

public record SmartIdLoginResponse(
    SmartIdLoginFlow flow,
    @Nullable String web2AppLink,
    @Nullable String verificationCode,
    String authenticationHash) {

  static SmartIdLoginResponse deviceLink(String web2AppLink, String authenticationHash) {
    return new SmartIdLoginResponse(
        SmartIdLoginFlow.DEVICE_LINK, web2AppLink, null, authenticationHash);
  }

  static SmartIdLoginResponse notification(String verificationCode, String authenticationHash) {
    return new SmartIdLoginResponse(
        SmartIdLoginFlow.NOTIFICATION, null, verificationCode, authenticationHash);
  }
}
