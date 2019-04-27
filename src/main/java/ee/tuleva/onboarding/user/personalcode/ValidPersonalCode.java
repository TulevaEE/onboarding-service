package ee.tuleva.onboarding.user.personalcode;


import javax.validation.constraints.NotBlank;

import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.ReportAsSingleViolation;
import javax.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
@Documented
@ReportAsSingleViolation
@Constraint(validatedBy = PersonalCodeValidator.class)
@NotBlank
@Size(min = 11, max = 11)
public @interface ValidPersonalCode {

  String message() default "{ee.tuleva.onboarding.user.personalcode.ValidPersonalCode.message}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

}
