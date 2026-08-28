package ee.tuleva.onboarding.account;

import static org.springframework.http.HttpStatus.GATEWAY_TIMEOUT;

import ee.tuleva.onboarding.error.response.ErrorsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccountErrorHandler {

  @ExceptionHandler(PensionRegistryAccountStatementConnectionException.class)
  public ResponseEntity<ErrorsResponse> handleErrors(
      PensionRegistryAccountStatementConnectionException exception) {
    log.error("PensionRegistryAccountStatementConnectionException {}", exception.toString());
    return new ResponseEntity<>(exception.getErrorsResponse(), GATEWAY_TIMEOUT);
  }
}
