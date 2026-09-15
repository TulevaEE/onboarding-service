package ee.tuleva.onboarding.auth.smartid;

import org.jspecify.annotations.Nullable;

public record SmartIdLoginResponse(
    SmartIdLoginFlow flow, @Nullable String web2AppLink, @Nullable String verificationCode) {

  static SmartIdLoginResponse deviceLink(String web2AppLink) {
    return new SmartIdLoginResponse(SmartIdLoginFlow.DEVICE_LINK, web2AppLink, null);
  }

  static SmartIdLoginResponse notification(String verificationCode) {
    return new SmartIdLoginResponse(SmartIdLoginFlow.NOTIFICATION, null, verificationCode);
  }
}
