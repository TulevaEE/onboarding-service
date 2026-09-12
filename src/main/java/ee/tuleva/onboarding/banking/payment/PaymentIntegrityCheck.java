package ee.tuleva.onboarding.banking.payment;

import org.jspecify.annotations.NullMarked;

@NullMarked
public enum PaymentIntegrityCheck {
  XSD_SCHEMA,
  UNSTRUCTURED_ADDRESS,
  FIELD_MISMATCH
}
