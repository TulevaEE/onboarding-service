package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;

import java.math.BigDecimal;

record UnitRegisterCash(
    BigDecimal incomingPaymentsClearing,
    BigDecimal unreconciledBankReceipts,
    BigDecimal fundUnitsReservedValue) {

  static final UnitRegisterCash NONE = new UnitRegisterCash(ZERO, ZERO, ZERO);

  BigDecimal liabilities() {
    return unreconciledBankReceipts.add(fundUnitsReservedValue);
  }
}
