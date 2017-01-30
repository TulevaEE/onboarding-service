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

    public static AuthenticateResponse fromMobileIDSession(MobileIDSession mobileIDSession) {
        AuthenticateResponseBuilder responseBuilder = AuthenticateResponse.builder()
                .mobileIdChallengeCode(mobileIDSession.challenge);

        return responseBuilder.build();
    }
}
