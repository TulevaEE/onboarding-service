package ee.tuleva.onboarding.aml.command;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.ReportAsSingleViolation;
import javax.validation.constraints.NotNull;

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
