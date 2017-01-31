package ee.tuleva.onboarding.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value= HttpStatus.CONTINUE, reason="AUTHENTICATION_NOT_COMPLETE") //<- TODO: this is not working
public class MobileIdAuthNotCompleteException extends RuntimeException {
    MobileIdAuthNotCompleteException() {
        super("AUTHENTICATION_NOT_COMPLETE");
    }
}
