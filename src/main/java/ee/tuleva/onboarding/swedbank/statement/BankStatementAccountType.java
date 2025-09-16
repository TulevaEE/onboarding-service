package ee.tuleva.onboarding.swedbank.statement;

import static ee.tuleva.onboarding.ledger.LedgerAccount.ServiceAccountType.DEPOSIT_EUR;

import ee.swedbank.gateway.iso.response.AccountStatement2;
import ee.tuleva.onboarding.ledger.LedgerAccount.ServiceAccountType;

public record BankStatementAccountType(String iban) {

  public ServiceAccountType getServiceAccountType() {
    return DEPOSIT_EUR;
  }

  public static BankStatementAccountType from(AccountStatement2 statement) {
    return new BankStatementAccountType(statement.getAcct().getId().getIBAN());
  }
}
