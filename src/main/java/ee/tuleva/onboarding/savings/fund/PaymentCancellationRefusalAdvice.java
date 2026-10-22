package ee.tuleva.onboarding.savings.fund;

import static org.springframework.http.HttpStatus.CONFLICT;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(assignableTypes = SavingFundPaymentController.class)
class PaymentCancellationRefusalAdvice {

  @ExceptionHandler(PaymentCancellationRefusedException.class)
  @ResponseStatus(CONFLICT)
  void refuse(PaymentCancellationRefusedException refusal) {
    log.info("Savings fund payment cancellation refused: error={}", refusal.getMessage());
  }
}
