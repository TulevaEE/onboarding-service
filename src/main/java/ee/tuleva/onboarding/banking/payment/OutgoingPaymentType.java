package ee.tuleva.onboarding.banking.payment;

public enum OutgoingPaymentType {
  /** Collection account to fund account: everything that has been given units. */
  SUBSCRIPTION_TRANSFER,
  /** Fund account to withdrawal account: the day's redemption total, funding the payouts. */
  REDEMPTION_TRANSFER,
  /** Withdrawal account to a client: one redemption payout. */
  PAYOUT,
  /**
   * Collection account back to the payer: a return, by request or because we rejected the money.
   */
  RETURN
}
