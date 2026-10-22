package ee.tuleva.onboarding.insurance;

import static org.springframework.http.HttpStatus.CONFLICT;

import java.util.Map;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = InsuranceTransferFormController.class)
class InsuranceTransferFormErrorHandler {

  @ExceptionHandler(PensionAccountNotReadyException.class)
  @ResponseStatus(CONFLICT)
  Map<String, String> pensionAccountNotReady() {
    return Map.of("error", "PENSION_ACCOUNT_NOT_READY");
  }
}
