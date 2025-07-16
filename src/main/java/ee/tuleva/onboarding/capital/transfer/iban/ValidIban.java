package ee.tuleva.onboarding.capital.transfer.iban;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Documented
@ReportAsSingleViolation
@Constraint(validatedBy = IbanValidator.class)
@NotBlank
public @interface ValidIban {

  String message() default "{ee.tuleva.onboarding.capital.transfer.iban.ValidIban.message}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
