package ee.tuleva.onboarding.investment.check.fee;

import java.math.BigDecimal;

record CustodianLineDifference(String accountName, BigDecimal report, BigDecimal nav) {

  BigDecimal difference() {
    return report.subtract(nav);
  }

  @Override
  public String toString() {
    return accountName
        + ": SEB report "
        + report.toPlainString()
        + ", our NAV "
        + nav.toPlainString()
        + ", difference "
        + difference().toPlainString();
  }
}
