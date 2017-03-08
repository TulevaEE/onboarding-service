package ee.tuleva.onboarding.auth.mobileid;

import org.springframework.security.oauth2.common.exceptions.ClientAuthenticationException;

public class MobileIdAuthNotCompleteException extends ClientAuthenticationException {
    MobileIdAuthNotCompleteException() {
        super("Please keep polling.");
    }

    @Override
    public String getOAuth2ErrorCode() {
        return "AUTHENTICATION_NOT_COMPLETE";
    }

    @Override
    public int getHttpErrorCode() {
        return 200; //TODO: change to something else
    }
}
