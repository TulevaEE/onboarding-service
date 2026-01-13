package ee.tuleva.onboarding.swedbank.statement;

import static ee.tuleva.onboarding.swedbank.SwedbankGatewayTime.SWEDBANK_GATEWAY_TIME_ZONE;
import static ee.tuleva.onboarding.swedbank.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.swedbank.statement.BankStatement.BankStatementType.INTRA_DAY_REPORT;

import ee.tuleva.onboarding.banking.iso20022.camt052.AccountReport11;
import ee.tuleva.onboarding.banking.iso20022.camt052.BankToCustomerAccountReportV02;
import ee.tuleva.onboarding.banking.iso20022.camt052.DateTimePeriodDetails;
import ee.tuleva.onboarding.banking.iso20022.camt053.AccountStatement2;
import ee.tuleva.onboarding.banking.iso20022.camt053.BankToCustomerStatementV02;
import java.time.Instant;
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
  private final Instant receivedBefore;

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

    DateTimePeriodDetails fromAndToDateTime =
        Require.notNull(report.getFrToDt(), "fromAndToDateTime");
    XMLGregorianCalendar toDateTime = Require.notNull(fromAndToDateTime.getToDtTm(), "toDateTime");

    var receivedBefore =
        toDateTime
            .toGregorianCalendar()
            .toZonedDateTime()
            .withZoneSameLocal(SWEDBANK_GATEWAY_TIME_ZONE)
            .toInstant();

    return new BankStatement(INTRA_DAY_REPORT, account, balances, entries, receivedBefore);
  }

  static BankStatement from(AccountStatement2 statement) {
    var accountType = BankStatementAccount.from(statement);
    var balances = statement.getBal().stream().map(BankStatementBalance::from).toList();
    var entries = statement.getNtry().stream().map(BankStatementEntry::from).toList();
    var receivedBefore =
        statement.getFrToDt() != null && statement.getFrToDt().getToDtTm() != null
            ? statement
                .getFrToDt()
                .getToDtTm()
                .toGregorianCalendar()
                .toZonedDateTime()
                .withZoneSameLocal(SWEDBANK_GATEWAY_TIME_ZONE)
                .toInstant()
            : null;

    return new BankStatement(HISTORIC_STATEMENT, accountType, balances, entries, receivedBefore);
  }
}
