package ee.tuleva.onboarding.banking.payment;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record PaymentIntegrityViolation(PaymentIntegrityCheck check, String field) {
  static PaymentIntegrityViolation mismatch(String field) {
    return new PaymentIntegrityViolation(PaymentIntegrityCheck.FIELD_MISMATCH, field);
  }

  public String summary() {
    return check + ":" + field;
  }
}
