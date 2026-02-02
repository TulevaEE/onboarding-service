package ee.tuleva.onboarding.banking.statement;

import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.INTRA_DAY_REPORT;

import ee.tuleva.onboarding.banking.iso20022.camt052.AccountReport11;
import ee.tuleva.onboarding.banking.iso20022.camt052.BankToCustomerAccountReportV02;
import ee.tuleva.onboarding.banking.iso20022.camt052.DateTimePeriodDetails;
import ee.tuleva.onboarding.banking.iso20022.camt053.AccountStatement2;
import ee.tuleva.onboarding.banking.iso20022.camt053.BankToCustomerStatementV02;
import java.time.ZoneId;
import java.util.List;
import javax.xml.datatype.XMLGregorianCalendar;
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

  public static BankStatement from(BankToCustomerAccountReportV02 accountReport, ZoneId timezone) {
    var report = Require.exactlyOne(accountReport.getRpt(), "report");
    return from(report, timezone);
  }

  public static BankStatement from(BankToCustomerStatementV02 customerStatement, ZoneId timezone) {
    var statement = Require.exactlyOne(customerStatement.getStmt(), "statement");
    return from(statement, timezone);
  }

  static BankStatement from(AccountReport11 report, ZoneId timezone) {
    var account = BankStatementAccount.from(report);
    var balances = report.getBal().stream().map(BankStatementBalance::from).toList();

    DateTimePeriodDetails fromAndToDateTime =
        Require.notNull(report.getFrToDt(), "fromAndToDateTime");
    XMLGregorianCalendar toDateTime = Require.notNull(fromAndToDateTime.getToDtTm(), "toDateTime");
    var receivedBefore =
        toDateTime.toGregorianCalendar().toZonedDateTime().withZoneSameLocal(timezone).toInstant();

    var entries =
        report.getNtry().stream()
            .map(entry -> BankStatementEntry.from(entry, receivedBefore))
            .toList();

    return new BankStatement(INTRA_DAY_REPORT, account, balances, entries);
  }

  static BankStatement from(AccountStatement2 statement, ZoneId timezone) {
    var accountType = BankStatementAccount.from(statement);
    var balances = statement.getBal().stream().map(BankStatementBalance::from).toList();
    var entries =
        statement.getNtry().stream()
            .map(entry -> BankStatementEntry.from(entry, timezone))
            .toList();

    return new BankStatement(HISTORIC_STATEMENT, accountType, balances, entries);
  }
}
