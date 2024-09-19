package ee.tuleva.onboarding.epis.mandate.details;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

@Constraint(validatedBy = ThreeLetterCountryCodeValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Valid3LetterCountryCode {
  String message() default "Invalid country code";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
