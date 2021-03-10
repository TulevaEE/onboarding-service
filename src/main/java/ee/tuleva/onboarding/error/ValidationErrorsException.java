package ee.tuleva.onboarding.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.validation.Errors;

@Getter
@AllArgsConstructor
public class ValidationErrorsException extends RuntimeException {

  private Errors errors;
}
