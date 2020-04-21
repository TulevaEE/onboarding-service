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
  @Deprecated String mobileIdChallengeCode;
  String challengeCode;

  public static AuthenticateResponse fromMobileIdSession(MobileIDSession mobileIDSession) {
    return builder()
        .mobileIdChallengeCode(mobileIDSession.getChallenge())
        .challengeCode(mobileIDSession.getChallenge())
        .build();
  }

  public static AuthenticateResponse fromSmartIdSession(SmartIdSession smartIdSession) {
    return builder().challengeCode(smartIdSession.getVerificationCode()).build();
  }
}
