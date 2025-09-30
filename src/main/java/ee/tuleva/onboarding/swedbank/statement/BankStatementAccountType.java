package ee.tuleva.onboarding.swedbank.statement;

import static ee.tuleva.onboarding.ledger.LedgerAccount.ServiceAccountType.DEPOSIT_EUR;

import ee.swedbank.gateway.iso.response.report.AccountReport11;
import ee.swedbank.gateway.iso.response.statement.AccountStatement2;
import ee.tuleva.onboarding.ledger.LedgerAccount.ServiceAccountType;

public record BankStatementAccountType(String iban) {

  public ServiceAccountType getServiceAccountType() {
    return DEPOSIT_EUR;
  }

  public static BankStatementAccountType from(AccountReport11 report) {
    return new BankStatementAccountType(report.getAcct().getId().getIBAN());
  }

  public static BankStatementAccountType from(AccountStatement2 statement) {
    return new BankStatementAccountType(statement.getAcct().getId().getIBAN());
  }
}
