package ee.tuleva.onboarding.mandate.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value= HttpStatus.NOT_FOUND, reason="Mandate not found")
public class MandateNotFoundException extends RuntimeException {

    public MandateNotFoundException() {
        super("Mandate not found");
    }
}
