package ee.tuleva.onboarding.aml.dto;


import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.ReportAsSingleViolation;
import javax.validation.constraints.NotNull;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

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
