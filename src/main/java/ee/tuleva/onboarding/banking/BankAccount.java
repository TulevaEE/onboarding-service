package ee.tuleva.onboarding.banking;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.ledger.SystemAccount;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record BankAccount(
    String iban, BankAccountType type, TulevaFund fund, String gatewayClientId) {

  public boolean matches(TulevaFund fund, BankAccountType type) {
    return this.fund == fund && this.type == type;
  }

  public boolean belongsTo(TulevaFund fund) {
    return this.fund == fund;
  }

  public SystemAccount ledgerAccount() {
    return type.getLedgerAccount();
  }

  @Override
  public String toString() {
    return fund + ":" + type;
  }
}
