package ee.tuleva.onboarding.banking.payment;

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
