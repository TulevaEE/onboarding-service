package ee.tuleva.onboarding.auth.response;

import ee.tuleva.onboarding.auth.mobileid.MobileIDSession;
import ee.tuleva.onboarding.auth.smartid.SmartIdSession;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AuthenticateResponse {

  private final String challengeCode;

  public static AuthenticateResponse fromMobileIdSession(MobileIDSession mobileIDSession) {
    return new AuthenticateResponse(mobileIDSession.getChallenge());
  }

  public static AuthenticateResponse fromSmartIdSession(SmartIdSession smartIdSession) {
    return new AuthenticateResponse(smartIdSession.getVerificationCode());
  }
}
