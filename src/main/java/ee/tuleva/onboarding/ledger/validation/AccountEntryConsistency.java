package ee.tuleva.onboarding.ledger.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validates that all entries in an account have the same asset type as the account. This ensures
 * consistency between an account and all its entries.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AccountEntryConsistencyValidator.class)
@Documented
public @interface AccountEntryConsistency {
  String message() default "All account entries must have the same asset type as the account";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
