package ee.tuleva.onboarding.oauth.server;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ConnectController.class)
class ConnectRejections {

  @ExceptionHandler(ConnectRejectedException.class)
  ResponseEntity<ErrorsResponse> rejected(ConnectRejectedException rejection) {
    return rejection.response();
  }
}
