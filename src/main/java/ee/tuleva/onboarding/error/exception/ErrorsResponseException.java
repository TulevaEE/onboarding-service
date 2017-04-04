package ee.tuleva.onboarding.error.exception;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Exception to propagate ErrorsResponse to ControllerAdvice
 */
@Getter
@AllArgsConstructor
public class ErrorsResponseException extends RuntimeException {

    ErrorsResponse errorsResponse;

}
