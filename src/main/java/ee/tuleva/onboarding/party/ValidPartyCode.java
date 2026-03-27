package ee.tuleva.onboarding.party;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
@Documented
@ReportAsSingleViolation
@Constraint(validatedBy = PartyCodeValidator.class)
@NotBlank
public @interface ValidPartyCode {

  String message() default "Invalid party code";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
