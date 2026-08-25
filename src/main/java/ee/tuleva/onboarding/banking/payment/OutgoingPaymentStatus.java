package ee.tuleva.onboarding.banking.payment;

public enum OutgoingPaymentStatus {
  /**
   * Written before the call and left this way if the call times out or the process dies: the
   * payment may or may not have executed. Never assume it did not.
   */
  ATTEMPTED,
  SUBMITTED,
  FAILED
}
