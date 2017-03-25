package ee.tuleva.onboarding.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.Errors;

@Getter
@AllArgsConstructor
//Recoverable exception thus not runtime exception
public class ValidationErrorsException extends Exception {

    private Errors errors;

}
