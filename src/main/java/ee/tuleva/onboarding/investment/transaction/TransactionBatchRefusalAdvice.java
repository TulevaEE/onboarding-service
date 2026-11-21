package ee.tuleva.onboarding.investment.transaction;

import static java.util.Objects.requireNonNullElse;
import static org.springframework.http.HttpStatus.CONFLICT;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(assignableTypes = TransactionCommandController.class)
class TransactionBatchRefusalAdvice {

  @ExceptionHandler(TransactionBatchRefusedException.class)
  ResponseEntity<String> refuse(TransactionBatchRefusedException refusal) {
    var reason = requireNonNullElse(refusal.getMessage(), refusal.toString());
    log.info("Transaction batch refused: reason={}", reason);
    return ResponseEntity.status(CONFLICT).body(reason);
  }
}
