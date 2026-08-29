package ee.tuleva.onboarding.mandate;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.exception.InvalidMandateException;
import ee.tuleva.onboarding.mandate.exception.MandateProcessingException;
import ee.tuleva.onboarding.signature.IdSessionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MandateErrorHandler {

  @ExceptionHandler(IdSessionException.class)
  public ResponseEntity<ErrorsResponse> handleErrors(IdSessionException exception) {
    log.warn("IdSessionException {}", exception.toString());
    return new ResponseEntity<>(exception.getErrorsResponse(), UNAUTHORIZED);
  }

  @ExceptionHandler(InvalidMandateException.class)
  public ResponseEntity<Object> handleErrors(InvalidMandateException exception) {
    log.debug("InvalidMandateException {}", exception.toString());
    return new ResponseEntity<>(exception.getErrorsResponse(), BAD_REQUEST);
  }

  @ExceptionHandler(MandateProcessingException.class)
  public ResponseEntity<Object> handleErrors(MandateProcessingException exception) {
    log.debug("MandateProcessingException {}", exception.toString());
    return new ResponseEntity<>(exception.getErrorsResponse(), INTERNAL_SERVER_ERROR);
  }
}
