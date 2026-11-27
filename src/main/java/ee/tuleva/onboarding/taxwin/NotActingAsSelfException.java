package ee.tuleva.onboarding.taxwin;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(FORBIDDEN)
class NotActingAsSelfException extends RuntimeException {

  NotActingAsSelfException() {
    super("Tax data is only for a person acting for themselves");
  }
}
