package ee.tuleva.onboarding.auth.response;

import com.codeborne.security.mobileid.MobileIDSession;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AuthenticateResponse {
    String mobileIdChallengeCode;

    public static AuthenticateResponse fromMobileIdSession(MobileIDSession mobileIDSession) {
        return builder()
                .mobileIdChallengeCode(mobileIDSession.challenge)
                .build();
    }
}
