package ee.tuleva.onboarding.signature;

import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import io.sentry.Sentry;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
class SigningServiceUnavailableHandler {

  @ExceptionHandler(SigningServiceUnavailableException.class)
  ResponseEntity<ErrorsResponse> handleSigningServiceUnavailable(
      SigningServiceUnavailableException exception) {
    final String RETRY_AFTER_SECONDS = "60";
    reportToSentry(exception);
    return ResponseEntity.status(SERVICE_UNAVAILABLE)
        .header(RETRY_AFTER, RETRY_AFTER_SECONDS)
        .body(
            ErrorsResponse.ofSingleError(
                "signature.service.unavailable",
                "Signing is temporarily unavailable, please retry later."));
  }

  private static void reportToSentry(SigningServiceUnavailableException exception) {
    String cause = exception.causeName();
    Sentry.withScope(
        scope -> {
          scope.setFingerprint(List.of("signing-trust-validation-unavailable"));
          scope.setTag("component", "digidoc4j-tsl");
          scope.setTag("cause", cause);
          log.error(
              "Signing unavailable, trust list, OCSP or timestamping service failed: cause={}",
              cause,
              exception);
        });
  }
}
