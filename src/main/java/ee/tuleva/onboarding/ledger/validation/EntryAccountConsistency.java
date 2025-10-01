package ee.tuleva.onboarding.ledger.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validates that an entry's asset type matches its account's asset type. This ensures consistency
 * between an entry and its associated account.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EntryAccountConsistencyValidator.class)
@Documented
public @interface EntryAccountConsistency {
  String message() default "Entry asset type must match its account's asset type";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
