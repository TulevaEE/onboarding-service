package ee.tuleva.onboarding.auth.smartid;

import jakarta.ws.rs.ServerErrorException;

class SmartIdSessionExpiredException extends RuntimeException {
  SmartIdSessionExpiredException(ServerErrorException cause) {
    super(cause);
  }
}
