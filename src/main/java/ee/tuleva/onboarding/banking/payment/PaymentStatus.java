package ee.tuleva.onboarding.banking.payment;

/** ISO 20022 external payment transaction status codes, as they appear in a pain.002. */
public enum PaymentStatus {
  ACCP,
  ACTC,
  ACSP,
  ACSC,
  ACWC,
  PDNG,
  RJCT,
  UNKNOWN;

  public boolean isRejection() {
    return this == RJCT;
  }

  public static PaymentStatus from(String code) {
    try {
      return valueOf(code.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return UNKNOWN;
    }
  }
}
