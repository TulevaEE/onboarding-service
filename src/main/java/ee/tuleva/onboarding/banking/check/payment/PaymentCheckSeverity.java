package ee.tuleva.onboarding.banking.check.payment;

public enum PaymentCheckSeverity {
  /** Money did not move and will not until someone acts. Shown on the approval brief. */
  HOLD,
  WARNING,
  INFO
}
