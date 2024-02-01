package ee.tuleva.onboarding.aml.dto;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotNull;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
@Documented
@ReportAsSingleViolation
@Constraint(validatedBy = AmlCheckTypeValidator.class)
@NotNull
public @interface ValidAmlCheckType {

  String message() default "{ee.tuleva.onboarding.aml.command.ValidAmlCheckType.message}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
