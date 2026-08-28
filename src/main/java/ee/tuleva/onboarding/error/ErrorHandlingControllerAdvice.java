package ee.tuleva.onboarding.error;

import static ee.tuleva.onboarding.error.SentryErrorCodeFingerprint.ERROR_CODE;
import static org.springframework.http.HttpStatus.*;

import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorResponseEntityFactory;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/*
  Order of @ExceptionHandler methods in this class is important
*/
@Slf4j
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ErrorHandlingControllerAdvice {

  private final ErrorResponseEntityFactory errorResponseEntityFactory =
      new ErrorResponseEntityFactory();

  @ExceptionHandler(ValidationErrorsException.class)
  public ResponseEntity<ErrorsResponse> handleErrors(ValidationErrorsException exception) {
    log.info("ValidationErrorsException {}", exception.toString());
    return errorResponseEntityFactory.fromErrors(exception.getErrors());
  }

  @ExceptionHandler(BindException.class)
  public ResponseEntity<ErrorsResponse> handleBindException(BindException exception) {
    log.info("BindException: {}", exception.toString());
    return errorResponseEntityFactory.fromErrors(exception);
  }

  @ExceptionHandler({
    CannotGetJdbcConnectionException.class,
    CannotCreateTransactionException.class
  })
  public ResponseEntity<ErrorsResponse> handleDatabaseConnectionUnavailable(Exception exception) {
    log.error(
        "Database connection unavailable (pool exhausted or database unreachable): {}",
        exception.toString());
    return ResponseEntity.status(SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, "5")
        .body(
            ErrorsResponse.ofSingleError(
                "service.unavailable", "Service temporarily unavailable, please retry."));
  }

  @ExceptionHandler(ErrorsResponseException.class)
  public ResponseEntity<Object> handleErrors(ErrorsResponseException exception) {
    logRejection(exception);
    return new ResponseEntity<>(exception.getErrorsResponse(), BAD_REQUEST);
  }

  private static void logRejection(ErrorsResponseException exception) {
    var errors = exception.getErrorsResponse().getErrors();
    if (errors.isEmpty()) {
      log.error("Request rejected: error={}", exception.toString());
      return;
    }
    var errorCode = errors.getFirst().getCode();
    var allExpected =
        ExpectedErrorCodes.areAllExpected(errors.stream().map(ErrorResponse::getCode).toList());
    MDC.put(ERROR_CODE, errorCode);
    try {
      if (allExpected) {
        log.info("Request rejected: code={}, error={}", errorCode, exception.toString());
      } else {
        log.error("Request rejected: code={}, error={}", errorCode, exception.toString());
      }
    } finally {
      MDC.remove(ERROR_CODE);
    }
  }
}
