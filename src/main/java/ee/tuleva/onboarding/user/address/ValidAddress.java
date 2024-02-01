package ee.tuleva.onboarding.user.address;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotNull;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

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
