package ee.tuleva.onboarding.swedbank.statement;

import static ee.tuleva.onboarding.swedbank.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.swedbank.statement.BankStatement.BankStatementType.INTRA_DAY_REPORT;

import ee.swedbank.gateway.iso.response.report.AccountReport11;
import ee.swedbank.gateway.iso.response.report.BankToCustomerAccountReportV02;
import ee.swedbank.gateway.iso.response.statement.AccountStatement2;
import ee.swedbank.gateway.iso.response.statement.BankToCustomerStatementV02;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class BankStatement {

  public enum BankStatementType {
    INTRA_DAY_REPORT,
    HISTORIC_STATEMENT
  }

  private final BankStatementType type;
  private final BankStatementAccount bankStatementAccount;
  private final List<BankStatementBalance> balances;
  // TODO check entries against TtlCdtNtries and TttlDbtEntries count from balances?
  private final List<BankStatementEntry> entries;

  static BankStatement from(BankToCustomerAccountReportV02 accountReport) {
    var report = Require.exactlyOne(accountReport.getRpt(), "report");
    return from(report);
  }

  public static BankStatement from(BankToCustomerStatementV02 customerStatement) {
    var statement = Require.exactlyOne(customerStatement.getStmt(), "statement");
    return from(statement);
  }

  static BankStatement from(AccountReport11 report) {
    var account = BankStatementAccount.from(report);
    var balances = report.getBal().stream().map(BankStatementBalance::from).toList();
    var entries = report.getNtry().stream().map(BankStatementEntry::from).toList();

    return new BankStatement(INTRA_DAY_REPORT, account, balances, entries);
  }

  static BankStatement from(AccountStatement2 statement) {
    var accountType = BankStatementAccount.from(statement);
    var balances = statement.getBal().stream().map(BankStatementBalance::from).toList();
    var entries = statement.getNtry().stream().map(BankStatementEntry::from).toList();

    return new BankStatement(HISTORIC_STATEMENT, accountType, balances, entries);
  }
}
