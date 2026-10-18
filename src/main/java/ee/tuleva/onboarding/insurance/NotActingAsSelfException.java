package ee.tuleva.onboarding.insurance;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(FORBIDDEN)
class NotActingAsSelfException extends RuntimeException {

  NotActingAsSelfException() {
    super("Insurance transfer forms are only for a person acting for themselves");
  }
}
