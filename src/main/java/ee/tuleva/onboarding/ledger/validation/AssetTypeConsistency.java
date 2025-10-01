package ee.tuleva.onboarding.ledger.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Validates that ledger entries have asset types matching their associated accounts. This ensures
 * consistency between accounts and their entries.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AssetTypeConsistencyValidator.class)
@Documented
public @interface AssetTypeConsistency {
  String message() default "Entry asset type must match account asset type";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
