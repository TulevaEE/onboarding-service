package ee.tuleva.onboarding.kyb;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({FIELD, METHOD, PARAMETER})
@Retention(RUNTIME)
@Documented
@ReportAsSingleViolation
@Constraint(validatedBy = RegistryCodeValidator.class)
@NotBlank
@Size(min = 8, max = 8)
public @interface ValidRegistryCode {

  String message() default "{ee.tuleva.onboarding.kyb.ValidRegistryCode.message}";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
