package ee.tuleva.onboarding.user.address;


import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.ReportAsSingleViolation;
import javax.validation.constraints.NotNull;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, METHOD, PARAMETER, TYPE, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Documented
@ReportAsSingleViolation
@Constraint(validatedBy = AddressValidator.class)
@NotNull
public @interface ValidAddress {

    String message() default "{ee.tuleva.onboarding.user.address.ValidAddress.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
