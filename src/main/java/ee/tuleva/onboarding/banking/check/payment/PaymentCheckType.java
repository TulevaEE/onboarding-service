package ee.tuleva.onboarding.banking.check.payment;

public enum PaymentCheckType {
  /** The generated file did not match the payment request, so nothing was sent. */
  PAYMENT_BLOCKED,
  /** The remitter was not one of our bank accounts, so no bank received the payment. */
  PAYMENT_MISROUTED,
  /** The bank told us it will not execute a payment we submitted. */
  PAYMENT_REJECTED,
  /** A debit from the payout account matching no redemption request. */
  PAYOUT_WITHOUT_REQUEST,
  /** The same redemption looks paid twice. */
  DUPLICATE_PAYOUT,
  /** Money left an account in a shape the system does not model. */
  UNMODELLED_DEBIT,
  /** The statement's own declared totals disagree with the entries we parsed. */
  STATEMENT_DOES_NOT_ADD_UP,
  /** A bank message we could not process at all, including a statement that does not add up. */
  STATEMENT_UNPROCESSABLE,
  /** Cash that reached the bank but has no place in the ledger, which corrupts NAV. */
  UNCLASSIFIED_BANK_OPERATION,
  /** An inbound payment that has not moved on in far too long. */
  PAYMENT_STUCK,
  /** A payment about to be issued units at a NAV that is not the one it is due. */
  WRONG_NAV_ISSUANCE,
  /** A submitted payment with no matching debit past its deadline. */
  PAYMENT_NOT_EXECUTED,
  /** A debit with no outgoing payment row behind it. */
  PHANTOM_DEBIT,
  /** A debit whose amount does not match what we logged. */
  DEBIT_MISMATCH,
  /** A payment whose fate we never learned. */
  PAYMENT_INDETERMINATE
}
