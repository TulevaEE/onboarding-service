package ee.tuleva.onboarding.auth;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import ee.tuleva.onboarding.auth.idcard.exception.IdCardSessionNotFoundException;
import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.mobileid.MobileIdSessionNotFoundException;
import ee.tuleva.onboarding.auth.principal.MinorCannotSelfAuthenticateException;
import ee.tuleva.onboarding.auth.response.AuthNotCompleteException;
import ee.tuleva.onboarding.auth.role.RoleSwitchAccessDeniedException;
import ee.tuleva.onboarding.auth.smartid.SmartIdSessionNotFoundException;
import ee.tuleva.onboarding.auth.webeid.WebEidAuthException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import io.jsonwebtoken.ExpiredJwtException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthErrorHandler {

  @ExceptionHandler({SmartIdSessionNotFoundException.class, MobileIdSessionNotFoundException.class})
  public ResponseEntity<ErrorsResponse> handleAuthSessionNotFound(RuntimeException exception) {
    log.info("Auth session not found: {}", exception.getMessage());
    return new ResponseEntity<>(
        ErrorsResponse.ofSingleError("auth.session.not.found", exception.getMessage()),
        UNAUTHORIZED);
  }

  @ExceptionHandler(AuthNotCompleteException.class)
  public ResponseEntity<Object> handleErrors(AuthNotCompleteException exception) {
    /*
     * Keeping the oauth error format. In the future this method should be removed and the exception
     * should be handled as other ErrorsResponseExceptions
     */
    return new ResponseEntity<>(
        Map.of("error", "AUTHENTICATION_NOT_COMPLETE", "error_description", "Please keep polling."),
        OK);
  }

  @ExceptionHandler(ExpiredJwtException.class)
  public ResponseEntity<Object> handleErrors(ExpiredJwtException exception) {
    log.debug("ExpiredJwtException {}", exception.toString());
    return new ResponseEntity<>(JwtTokenUtil.getExpiredTokenErrorResponse(), UNAUTHORIZED);
  }

  @ExceptionHandler(ExpiredRefreshJwtException.class)
  public ResponseEntity<Object> handleErrors(ExpiredRefreshJwtException exception) {
    log.debug("ExpiredJwtException {}", exception.toString());
    return new ResponseEntity<>(
        Map.of(
            "error", "REFRESH_TOKEN_EXPIRED", "error_description", "The refresh token is expired."),
        FORBIDDEN);
  }

  @ExceptionHandler(RoleSwitchAccessDeniedException.class)
  public ResponseEntity<Object> handleErrors(RoleSwitchAccessDeniedException exception) {
    log.info("RoleSwitchAccessDeniedException: {}", exception.getMessage());
    return new ResponseEntity<>(
        Map.of("error", "ROLE_SWITCH_DENIED", "error_description", exception.getMessage()),
        FORBIDDEN);
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
