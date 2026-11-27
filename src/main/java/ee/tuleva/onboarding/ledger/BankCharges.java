package ee.tuleva.onboarding.ledger;

import java.math.BigDecimal;

public record BankCharges(BigDecimal bankFees, BigDecimal overdraftInterest) {

  public BigDecimal total() {
    return bankFees.add(overdraftInterest);
  }
}
