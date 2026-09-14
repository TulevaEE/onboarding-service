package ee.tuleva.onboarding.banking.payment;

import java.util.Arrays;

public enum PaymentStatus {
  ACCEPTED_CUSTOMER_PROFILE("ACCP"),
  ACCEPTED_TECHNICAL_VALIDATION("ACTC"),
  ACCEPTED_SETTLEMENT_IN_PROCESS("ACSP"),
  ACCEPTED_SETTLEMENT_COMPLETED("ACSC"),
  ACCEPTED_WITH_CHANGE("ACWC"),
  PENDING("PDNG"),
  REJECTED("RJCT"),
  UNKNOWN("");

  private final String isoCode;

  PaymentStatus(String isoCode) {
    this.isoCode = isoCode;
  }

  public boolean isRejection() {
    return this == REJECTED;
  }

  public static PaymentStatus from(String reportedCode) {
    var normalisedCode = reportedCode.trim().toUpperCase();
    return Arrays.stream(values())
        .filter(status -> status.isoCode.equals(normalisedCode))
        .findFirst()
        .orElse(UNKNOWN);
  }
}
