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
    return new ResponseEntity<>(
        Map.of(
            "error", "HACKATHON_REGISTRATION_CLOSED", "error_description", exception.getMessage()),
        BAD_REQUEST);
  }
}
