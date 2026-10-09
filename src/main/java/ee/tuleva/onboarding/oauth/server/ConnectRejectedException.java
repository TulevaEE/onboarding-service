package ee.tuleva.onboarding.oauth.server;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ConnectRejectedException extends RuntimeException {

  private final Reason reason;

  private ConnectRejectedException(Reason reason) {
    super(reason.message);
    this.reason = reason;
  }

  static ConnectRejectedException requestNotFound() {
    return new ConnectRejectedException(Reason.REQUEST_NOT_FOUND);
  }

  static ConnectRejectedException loginRequired() {
    return new ConnectRejectedException(Reason.LOGIN_REQUIRED);
  }

  ResponseEntity<ErrorsResponse> response() {
    return reason.response();
  }

  private enum Reason {
    REQUEST_NOT_FOUND(
        NOT_FOUND,
        "connect.request.not.found",
        "Connect request is unknown, expired, used or from another browser"),
    LOGIN_REQUIRED(
        FORBIDDEN,
        "connect.login.required",
        "Approving needs a fresh Smart-ID, Mobile-ID or ID-card login as yourself");

    private final HttpStatus status;
    private final String code;
    private final String message;

    Reason(HttpStatus status, String code, String message) {
      this.status = status;
      this.code = code;
      this.message = message;
    }

    ResponseEntity<ErrorsResponse> response() {
      return ResponseEntity.status(status).body(ErrorsResponse.ofSingleError(code, message));
    }
  }
}
