package ee.tuleva.onboarding.savings.fund.gift;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import java.util.Map;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ReceivedGiftController.class)
class ReceivedGiftRefusalAdvice {

  private static final Map<String, String> NOT_ACTING_AS_SELF =
      Map.of(
          "error",
          "NOT_ACTING_AS_SELF",
          "error_description",
          "Only a person acting as themselves can read the gifts made to them");

  @ExceptionHandler(NotActingAsSelfException.class)
  @ResponseStatus(FORBIDDEN)
  Map<String, String> notActingAsSelf() {
    return NOT_ACTING_AS_SELF;
  }
}
