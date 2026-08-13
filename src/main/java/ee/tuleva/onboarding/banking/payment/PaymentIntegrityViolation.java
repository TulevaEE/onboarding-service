package ee.tuleva.onboarding.banking.payment;

import org.jspecify.annotations.NullMarked;

/**
 * Deliberately carries no expected/actual values. A violation is reported to Slack and Sentry, and
 * the values it would carry are client IBANs and names, so the type cannot hold them at all.
 */
@NullMarked
public record PaymentIntegrityViolation(PaymentIntegrityCheck check, String field) {

  static PaymentIntegrityViolation mismatch(String field) {
    return new PaymentIntegrityViolation(PaymentIntegrityCheck.FIELD_MISMATCH, field);
  }

  public String summary() {
    return check + ":" + field;
  }
}
