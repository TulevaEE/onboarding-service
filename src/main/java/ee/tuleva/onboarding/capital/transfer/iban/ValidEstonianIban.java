package ee.tuleva.onboarding.capital.transfer.iban;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = {})
@ValidIban
@Pattern(regexp = "EE.*", message = "IBAN must be an Estonian IBAN (starting with EE).")
public @interface ValidEstonianIban {

  String message() default "Invalid Estonian IBAN.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
