package ee.tuleva.onboarding.company;

import static org.springframework.http.HttpStatus.NOT_FOUND;

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
public class CompanyErrorHandler {

  @ExceptionHandler(CompanyNotFoundException.class)
  public ResponseEntity<Object> handleErrors(CompanyNotFoundException exception) {
    log.info("CompanyNotFoundException: {}", exception.getMessage());
    return new ResponseEntity<>(
        Map.of("error", "COMPANY_NOT_FOUND", "error_description", exception.getMessage()),
        NOT_FOUND);
  }
}
