package ee.tuleva.onboarding.error;

import static org.springframework.http.HttpStatus.*;

import ee.tuleva.onboarding.account.PensionRegistryAccountStatementConnectionException;
import ee.tuleva.onboarding.auth.ExpiredRefreshJwtException;
import ee.tuleva.onboarding.auth.idcard.exception.IdCardSessionNotFoundException;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException;
import ee.tuleva.onboarding.auth.webeid.WebEidAuthException;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorResponseEntityFactory;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.exception.IdSessionException;
import ee.tuleva.onboarding.mandate.exception.InvalidMandateException;
import ee.tuleva.onboarding.mandate.exception.MandateProcessingException;
import io.jsonwebtoken.ExpiredJwtException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/*
  Order of @ExceptionHandler methods in this class is important
*/
@Slf4j
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErrorHandlingControllerAdvice {

  private final ErrorResponseEntityFactory errorResponseEntityFactory =
      new ErrorResponseEntityFactory();

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
  public ResponseEntity<ErrorsResponse> handleErrors(
      PensionRegistryAccountStatementConnectionException exception) {
    log.error("PensionRegistryAccountStatementConnectionException {}", exception.toString());
    return new ResponseEntity<>(exception.getErrorsResponse(), GATEWAY_TIMEOUT);
  }

  @ExceptionHandler(ErrorsResponseException.class)
  public ResponseEntity<Object> handleErrors(ErrorsResponseException exception) {
    if (exception instanceof AuthNotCompleteException) {
      /*
       * Keeping the oauth error format. In the future this method should be removed and the exception
       * should be handled as other ErrorsResponseExceptions
       */
      return new ResponseEntity<>(
          Map.of(
              "error", "AUTHENTICATION_NOT_COMPLETE", "error_description", "Please keep polling."),
          OK);
    }
    log.error("{}", exception.toString());
    return new ResponseEntity<>(exception.getErrorsResponse(), BAD_REQUEST);
  }

  @ExceptionHandler(ExpiredJwtException.class)
  public ResponseEntity<Object> handleErrors(ExpiredJwtException exception) {
    log.debug("ExpiredJwtException {}", exception.toString());
    return new ResponseEntity<>(
        JwtTokenUtil.getExpiredTokenErrorResponse(), HttpStatus.UNAUTHORIZED);
  }

  @ExceptionHandler(ExpiredRefreshJwtException.class)
  public ResponseEntity<Object> handleErrors(ExpiredRefreshJwtException exception) {
    log.debug("ExpiredJwtException {}", exception.toString());
    return new ResponseEntity<>(
        Map.of(
            "error", "REFRESH_TOKEN_EXPIRED", "error_description", "The refresh token is expired."),
        FORBIDDEN);
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

  @ExceptionHandler(WebEidAuthException.class)
  public ResponseEntity<Object> handleErrors(WebEidAuthException exception) {
    log.info("WebEidAuthException {}", exception.getMessage());
    return idCardAuthFailedResponse();
  }

  @ExceptionHandler(IdCardSessionNotFoundException.class)
  public ResponseEntity<Object> handleErrors(IdCardSessionNotFoundException exception) {
    log.info("IdCardSessionNotFoundException {}", exception.getMessage());
    return idCardAuthFailedResponse();
  }

  private ResponseEntity<Object> idCardAuthFailedResponse() {
    return new ResponseEntity<>(
        Map.of(
            "error", "ID_CARD_AUTH_FAILED", "error_description", "ID-card authentication failed"),
        BAD_REQUEST);
  }
}
