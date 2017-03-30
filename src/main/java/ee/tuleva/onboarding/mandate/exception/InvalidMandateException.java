package ee.tuleva.onboarding.mandate.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value= HttpStatus.UNPROCESSABLE_ENTITY, reason="Invalid mandate. Can't transfer more than 100% from source fund.")
public class InvalidMandateException extends RuntimeException {

    public InvalidMandateException() {
        super("Invalid mandate. Can't transfer more than 100% from source fund.");
    }

}
