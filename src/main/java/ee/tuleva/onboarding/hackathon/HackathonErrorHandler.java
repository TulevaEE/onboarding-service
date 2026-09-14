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

  @ExceptionHandler(HackathonException.class)
  public ResponseEntity<Object> handleErrors(HackathonException exception) {
    log.info("{}: {}", exception.getClass().getSimpleName(), exception.getMessage());
    return new ResponseEntity<>(
        Map.of("error", exception.getErrorCode(), "error_description", exception.getMessage()),
        BAD_REQUEST);
  }
}
