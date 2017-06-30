package ee.tuleva.onboarding.error;

import ee.tuleva.onboarding.account.PensionRegistryAccountStatementConnectionException;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorResponseEntityFactory;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.exception.IdSessionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/*
   Order of @ExceptionHandler methods in this class is important
 */
@Slf4j
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ErrorHandlingControllerAdvice {

    private final ErrorResponseEntityFactory errorResponseEntityFactory;

    @ExceptionHandler(ValidationErrorsException.class)
    public ResponseEntity<ErrorsResponse> handleErrors(ValidationErrorsException exception) {
        log.info("ValidationErrorsException {}", exception.toString());
        return errorResponseEntityFactory.fromErrors(exception.getErrors());
    }

    @ExceptionHandler(IdSessionException.class)
    public ResponseEntity<ErrorsResponse> handleErrors(IdSessionException exception) {
        log.info("IdSessionException {}", exception.toString());
        return new ResponseEntity<>(exception.getErrorsResponse(), UNAUTHORIZED);
    }

    @ExceptionHandler(PensionRegistryAccountStatementConnectionException.class)
    public ResponseEntity<ErrorsResponse> handleErrors(PensionRegistryAccountStatementConnectionException exception) {
        log.info("PensionRegistryAccountStatementConnectionException {}", exception.toString());
        return new ResponseEntity<>(exception.getErrorsResponse(), GATEWAY_TIMEOUT);
    }

    @ExceptionHandler(ErrorsResponseException.class)
    public ResponseEntity<ErrorsResponse> handleErrors(ErrorsResponseException exception) {
        log.info("ErrorsResponseException {}", exception.toString());
        return new ResponseEntity<>(exception.getErrorsResponse(), BAD_REQUEST);
    }

}
