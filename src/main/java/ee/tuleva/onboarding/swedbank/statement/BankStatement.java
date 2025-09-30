package ee.tuleva.onboarding.swedbank.statement;

import static lombok.AccessLevel.PRIVATE;

import ee.swedbank.gateway.iso.response.AccountReport11;
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

  // TODO camt 053 for previous reports also here?
  public static BankStatement from(AccountReport11 report) {
    var accountType = BankStatementAccountType.from(report);
    var balances = report.getBal().stream().map(BankStatementBalance::from).toList();
    var entries = report.getNtry().stream().map(BankStatementEntry::from).toList();

    return new BankStatement(accountType, balances, entries);
  }
}
