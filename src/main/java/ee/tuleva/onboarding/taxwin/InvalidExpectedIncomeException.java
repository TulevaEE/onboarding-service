package ee.tuleva.onboarding.taxwin;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.time.YearMonth;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(BAD_REQUEST)
class InvalidExpectedIncomeException extends RuntimeException {

  InvalidExpectedIncomeException(YearMonth month) {
    super("Expected income cannot be set for this month: month=" + month);
  }

  InvalidExpectedIncomeException(int year) {
    super("Yearly income cannot be spread over the months not declared yet: year=" + year);
  }
}
