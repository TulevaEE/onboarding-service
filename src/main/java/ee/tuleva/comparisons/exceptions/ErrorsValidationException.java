package ee.tuleva.comparisons.exceptions;


import org.springframework.http.HttpStatus;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value=HttpStatus.BAD_REQUEST, reason = "Malformed parameter syntax")
public class ErrorsValidationException extends Exception{

    Errors errors;

    public ErrorsValidationException(Errors errors) {
        this.errors = errors;
    }

}
