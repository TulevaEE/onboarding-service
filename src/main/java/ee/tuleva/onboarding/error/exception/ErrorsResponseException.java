package ee.tuleva.onboarding.error.exception;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import lombok.Getter;
import lombok.ToString;

/**
 * Exception to propagate ErrorsResponse to ControllerAdvice
 */
@Getter
@ToString
public class ErrorsResponseException extends RuntimeException {

    protected ErrorsResponse errorsResponse;

    public ErrorsResponseException(ErrorsResponse errorsResponse) {
        super(errorsResponse.toString());
        this.errorsResponse = errorsResponse;
    }

}
