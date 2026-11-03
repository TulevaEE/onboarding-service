package ee.tuleva.onboarding.savings.fund;

import static org.springframework.http.HttpStatus.CONFLICT;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(assignableTypes = SavingsFundAccountClosureController.class)
class SavingsFundAccountClosureRefusalAdvice {

  @ExceptionHandler(SavingsFundAccountClosureRefusedException.class)
  @ResponseStatus(CONFLICT)
  void refuse(SavingsFundAccountClosureRefusedException refusal) {
    log.info("Savings fund account closure refused: error={}", refusal.getMessage());
  }
}
