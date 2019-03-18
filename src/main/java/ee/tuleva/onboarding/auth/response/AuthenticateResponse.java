package ee.tuleva.onboarding.auth.response;

import com.codeborne.security.mobileid.MobileIDSession;
import ee.tuleva.onboarding.auth.smartid.SmartIdSession;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
public class AuthenticateResponse {
    @Deprecated
    String mobileIdChallengeCode;
    String challengeCode;

    public static AuthenticateResponse fromMobileIdSession(MobileIDSession mobileIDSession) {
        return builder()
                .mobileIdChallengeCode(mobileIDSession.challenge)
                .challengeCode(mobileIDSession.challenge)
                .build();
    }

    public static AuthenticateResponse fromSmartIdSession(SmartIdSession smartIdSession) {
        return builder()
                .challengeCode(smartIdSession.verificationCode)
                .build();
    }
}
