package ee.tuleva.onboarding.hackathon;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

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
public class HackathonErrorHandler {

  @ExceptionHandler(HackathonRegistrationClosedException.class)
  public ResponseEntity<Object> handleErrors(HackathonRegistrationClosedException exception) {
    log.info("HackathonRegistrationClosedException: {}", exception.getMessage());
    return badRequest("HACKATHON_REGISTRATION_CLOSED", exception);
  }

  @ExceptionHandler(HackathonIdeaSubmissionClosedException.class)
  public ResponseEntity<Object> handleErrors(HackathonIdeaSubmissionClosedException exception) {
    log.info("HackathonIdeaSubmissionClosedException: {}", exception.getMessage());
    return badRequest("HACKATHON_IDEA_SUBMISSION_CLOSED", exception);
  }

  @ExceptionHandler(HackathonRegistrationRequiredException.class)
  public ResponseEntity<Object> handleErrors(HackathonRegistrationRequiredException exception) {
    log.info("HackathonRegistrationRequiredException: {}", exception.getMessage());
    return badRequest("HACKATHON_REGISTRATION_REQUIRED", exception);
  }

  private static ResponseEntity<Object> badRequest(String error, RuntimeException exception) {
    return new ResponseEntity<>(
        Map.of("error", error, "error_description", exception.getMessage()), BAD_REQUEST);
  }
}
