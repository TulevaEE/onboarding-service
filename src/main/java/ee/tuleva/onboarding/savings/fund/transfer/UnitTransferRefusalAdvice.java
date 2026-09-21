package ee.tuleva.onboarding.savings.fund.transfer;

import static java.util.Objects.requireNonNullElse;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = UnitTransferController.class)
class UnitTransferRefusalAdvice {

  @ExceptionHandler({
    NoSuchElementException.class,
    IllegalArgumentException.class,
    IllegalStateException.class
  })
  ResponseEntity<String> refuse(RuntimeException refusal) {
    HttpStatus status =
        switch (refusal) {
          case NoSuchElementException _ -> NOT_FOUND;
          case IllegalStateException _ -> CONFLICT;
          default -> BAD_REQUEST;
        };
    return ResponseEntity.status(status)
        .body(requireNonNullElse(refusal.getMessage(), refusal.toString()));
  }
}
