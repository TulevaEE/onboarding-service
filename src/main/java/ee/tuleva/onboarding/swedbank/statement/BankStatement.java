package ee.tuleva.onboarding.swedbank.statement;

import static lombok.AccessLevel.PRIVATE;

import ee.swedbank.gateway.iso.response.AccountStatement2;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = PRIVATE)
public class BankStatement {

  private final BankStatementAccountType bankStatementAccountType;
  private final List<BankStatementBalance> balances;
  // TODO check entries against TtlCdtNtries and TttlDbtEntries count from balances?
  private final List<BankStatementEntry> entries;

  public static BankStatement from(AccountStatement2 statement) {
    var accountType = BankStatementAccountType.from(statement);
    var balances = statement.getBal().stream().map(BankStatementBalance::from).toList();
    var entries = statement.getNtry().stream().map(BankStatementEntry::from).toList();

    return new BankStatement(accountType, balances, entries);
  }
}
