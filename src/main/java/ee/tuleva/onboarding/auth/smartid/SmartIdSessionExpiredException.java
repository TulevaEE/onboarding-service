package ee.tuleva.onboarding.auth.smartid;

import jakarta.ws.rs.ServerErrorException;

class SmartIdSessionExpiredException extends RuntimeException {
  private final int status;

  SmartIdSessionExpiredException(ServerErrorException cause) {
    super(cause);
    this.status = cause.getResponse().getStatus();
  }

  int status() {
    return status;
  }
}
