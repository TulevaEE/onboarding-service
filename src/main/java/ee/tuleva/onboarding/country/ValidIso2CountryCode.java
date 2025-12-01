package ee.tuleva.onboarding.country;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = Iso2CountryCodeValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIso2CountryCode {
  String message() default "Invalid ISO2 country code";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
