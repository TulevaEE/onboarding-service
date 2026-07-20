package ee.tuleva.onboarding.error;

import static ee.tuleva.onboarding.error.SentryErrorCodeFingerprint.ERROR_CODE;
import static org.springframework.http.HttpStatus.*;

import ee.tuleva.onboarding.account.PensionRegistryAccountStatementConnectionException;
import ee.tuleva.onboarding.auth.ExpiredRefreshJwtException;
import ee.tuleva.onboarding.auth.idcard.exception.IdCardSessionNotFoundException;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.mobileid.MobileIdSessionNotFoundException;
import ee.tuleva.onboarding.auth.principal.MinorCannotSelfAuthenticateException;
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException;
import ee.tuleva.onboarding.auth.role.RoleSwitchAccessDeniedException;
import ee.tuleva.onboarding.auth.smartid.SmartIdSessionNotFoundException;
import ee.tuleva.onboarding.auth.webeid.WebEidAuthException;
import ee.tuleva.onboarding.company.CompanyNotFoundException;
import ee.tuleva.onboarding.error.exception.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorResponseEntityFactory;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.exception.IdSessionException;
import ee.tuleva.onboarding.mandate.exception.InvalidMandateException;
import ee.tuleva.onboarding.mandate.exception.MandateProcessingException;
import ee.tuleva.onboarding.party.ChildIsNotAMinorException;
import io.jsonwebtoken.ExpiredJwtException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
@Order(Ordered.HIGHEST_PRECEDENCE)
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

  @ExceptionHandler(IdSessionException.class)
  public ResponseEntity<ErrorsResponse> handleErrors(IdSessionException exception) {
    log.warn("IdSessionException {}", exception.toString());
    return new ResponseEntity<>(exception.getErrorsResponse(), UNAUTHORIZED);
  }

  @ExceptionHandler({SmartIdSessionNotFoundException.class, MobileIdSessionNotFoundException.class})
  public ResponseEntity<ErrorsResponse> handleAuthSessionNotFound(RuntimeException exception) {
    log.error("Auth session not found: {}", exception.getMessage());
    return new ResponseEntity<>(
        ErrorsResponse.ofSingleError("auth.session.not.found", exception.getMessage()),
        UNAUTHORIZED);
  }

  @ExceptionHandler(PensionRegistryAccountStatementConnectionException.class)
  public ResponseEntity<ErrorsResponse> handleErrors(
      PensionRegistryAccountStatementConnectionException exception) {
    log.error("PensionRegistryAccountStatementConnectionException {}", exception.toString());
    return new ResponseEntity<>(exception.getErrorsResponse(), GATEWAY_TIMEOUT);
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
    MDC.put(ERROR_CODE, errorCode);
    try {
      log.error("Request rejected: code={}, error={}", errorCode, exception.toString());
    } finally {
      MDC.remove(ERROR_CODE);
    }
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

  @ExceptionHandler(CompanyNotFoundException.class)
  public ResponseEntity<Object> handleErrors(CompanyNotFoundException exception) {
    log.info("CompanyNotFoundException: {}", exception.getMessage());
    return new ResponseEntity<>(
        Map.of("error", "COMPANY_NOT_FOUND", "error_description", exception.getMessage()),
        NOT_FOUND);
  }

  @ExceptionHandler(RoleSwitchAccessDeniedException.class)
  public ResponseEntity<Object> handleErrors(RoleSwitchAccessDeniedException exception) {
    log.info("RoleSwitchAccessDeniedException: {}", exception.getMessage());
    return new ResponseEntity<>(
        Map.of("error", "ROLE_SWITCH_DENIED", "error_description", exception.getMessage()),
        FORBIDDEN);
  }

  @ExceptionHandler(ChildIsNotAMinorException.class)
  public ResponseEntity<Object> handleErrors(ChildIsNotAMinorException exception) {
    log.info("ChildIsNotAMinorException: {}", exception.getMessage());
    return new ResponseEntity<>(
        Map.of("error", "CHILD_IS_NOT_A_MINOR", "error_description", exception.getMessage()),
        BAD_REQUEST);
  }

  @ExceptionHandler(MinorCannotSelfAuthenticateException.class)
  public ResponseEntity<Object> handleErrors(MinorCannotSelfAuthenticateException exception) {
    log.info("MinorCannotSelfAuthenticateException: {}", exception.getMessage());
    return new ResponseEntity<>(
        Map.of(
            "error", "MINOR_CANNOT_SELF_AUTHENTICATE", "error_description", exception.getMessage()),
        FORBIDDEN);
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
