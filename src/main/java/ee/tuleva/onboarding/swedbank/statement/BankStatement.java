package ee.tuleva.onboarding.swedbank.statement;

import static ee.tuleva.onboarding.swedbank.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.swedbank.statement.BankStatement.BankStatementType.INTRA_DAY_REPORT;
import static lombok.AccessLevel.PRIVATE;

import ee.swedbank.gateway.iso.response.report.AccountReport11;
import ee.swedbank.gateway.iso.response.statement.AccountStatement2;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = PRIVATE)
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

  public static BankStatement from(AccountReport11 report) {
    var account = BankStatementAccount.from(report);
    var balances = report.getBal().stream().map(BankStatementBalance::from).toList();
    var entries = report.getNtry().stream().map(BankStatementEntry::from).toList();

    return new BankStatement(INTRA_DAY_REPORT, account, balances, entries);
  }

  public static BankStatement from(AccountStatement2 statement) {
    var accountType = BankStatementAccount.from(statement);
    var balances = statement.getBal().stream().map(BankStatementBalance::from).toList();
    var entries = statement.getNtry().stream().map(BankStatementEntry::from).toList();

    return new BankStatement(HISTORIC_STATEMENT, accountType, balances, entries);
  }
}
