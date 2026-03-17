package ee.tuleva.onboarding.banking.statement;

import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.statement.BankStatement.BankStatementType.INTRA_DAY_REPORT;
import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.CLOSE;
import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.OPEN;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.banking.iso20022.camt052.AccountReport11;
import ee.tuleva.onboarding.banking.iso20022.camt052.BankToCustomerAccountReportV02;
import ee.tuleva.onboarding.banking.iso20022.camt052.DateTimePeriodDetails;
import ee.tuleva.onboarding.banking.iso20022.camt053.AccountStatement2;
import ee.tuleva.onboarding.banking.iso20022.camt053.BankToCustomerStatementV02;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import javax.xml.datatype.XMLGregorianCalendar;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
@RequiredArgsConstructor
public class BankStatement {

  private static final Logger log = LoggerFactory.getLogger(BankStatement.class);

  public enum BankStatementType {
    INTRA_DAY_REPORT,
    HISTORIC_STATEMENT
  }

  private final BankStatementType type;
  private final BankStatementAccount bankStatementAccount;
  private final List<BankStatementBalance> balances;
  private final List<BankStatementEntry> entries;

  record TransactionSummary(
      @Nullable String totalCount,
      @Nullable BigDecimal totalSum,
      @Nullable String creditCount,
      @Nullable BigDecimal creditSum,
      @Nullable String debitCount,
      @Nullable BigDecimal debitSum) {

    static @Nullable TransactionSummary from(
        @Nullable ee.tuleva.onboarding.banking.iso20022.camt053.TotalTransactions2 s) {
      if (s == null) return null;
      return new TransactionSummary(
          s.getTtlNtries() != null ? s.getTtlNtries().getNbOfNtries() : null,
          s.getTtlNtries() != null ? s.getTtlNtries().getSum() : null,
          s.getTtlCdtNtries() != null ? s.getTtlCdtNtries().getNbOfNtries() : null,
          s.getTtlCdtNtries() != null ? s.getTtlCdtNtries().getSum() : null,
          s.getTtlDbtNtries() != null ? s.getTtlDbtNtries().getNbOfNtries() : null,
          s.getTtlDbtNtries() != null ? s.getTtlDbtNtries().getSum() : null);
    }

    static @Nullable TransactionSummary from(
        @Nullable ee.tuleva.onboarding.banking.iso20022.camt052.TotalTransactions2 s) {
      if (s == null) return null;
      return new TransactionSummary(
          s.getTtlNtries() != null ? s.getTtlNtries().getNbOfNtries() : null,
          s.getTtlNtries() != null ? s.getTtlNtries().getSum() : null,
          s.getTtlCdtNtries() != null ? s.getTtlCdtNtries().getNbOfNtries() : null,
          s.getTtlCdtNtries() != null ? s.getTtlCdtNtries().getSum() : null,
          s.getTtlDbtNtries() != null ? s.getTtlDbtNtries().getNbOfNtries() : null,
          s.getTtlDbtNtries() != null ? s.getTtlDbtNtries().getSum() : null);
    }
  }

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

    var summary = TransactionSummary.from(report.getTxsSummry());
    var mismatches = validateEntries(entries, summary, balances);
    mismatches.forEach(
        m -> log.error("Bank statement integrity check failed: account={}, {}", account.iban(), m));

    return new BankStatement(INTRA_DAY_REPORT, account, balances, entries);
  }

  static BankStatement from(AccountStatement2 statement, ZoneId timezone) {
    var account = BankStatementAccount.from(statement);
    var balances = statement.getBal().stream().map(BankStatementBalance::from).toList();
    var entries =
        statement.getNtry().stream()
            .map(entry -> BankStatementEntry.from(entry, timezone))
            .toList();

    var summary = TransactionSummary.from(statement.getTxsSummry());
    var mismatches = validateEntries(entries, summary, balances);
    mismatches.forEach(
        m -> log.error("Bank statement integrity check failed: account={}, {}", account.iban(), m));

    return new BankStatement(HISTORIC_STATEMENT, account, balances, entries);
  }

  static List<String> validateEntries(
      List<BankStatementEntry> entries,
      @Nullable TransactionSummary summary,
      List<BankStatementBalance> balances) {

    var mismatches = new ArrayList<String>();
    if (summary == null) return mismatches;

    long creditCount = entries.stream().filter(e -> e.amount().signum() > 0).count();
    long debitCount = entries.stream().filter(e -> e.amount().signum() < 0).count();
    BigDecimal creditSum =
        entries.stream()
            .filter(e -> e.amount().signum() > 0)
            .map(BankStatementEntry::amount)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal debitSum =
        entries.stream()
            .filter(e -> e.amount().signum() < 0)
            .map(e -> e.amount().abs())
            .reduce(ZERO, BigDecimal::add);

    if (summary.totalCount() != null && entries.size() != Integer.parseInt(summary.totalCount())) {
      mismatches.add(
          "total entry count mismatch: expected=%s, actual=%d"
              .formatted(summary.totalCount(), entries.size()));
    }

    if (summary.creditCount() != null && creditCount != Integer.parseInt(summary.creditCount())) {
      mismatches.add(
          "credit entry count mismatch: expected=%s, actual=%d"
              .formatted(summary.creditCount(), creditCount));
    }

    if (summary.debitCount() != null && debitCount != Integer.parseInt(summary.debitCount())) {
      mismatches.add(
          "debit entry count mismatch: expected=%s, actual=%d"
              .formatted(summary.debitCount(), debitCount));
    }

    if (summary.creditSum() != null && summary.creditSum().compareTo(creditSum) != 0) {
      mismatches.add(
          "credit sum mismatch: expected=%s, actual=%s".formatted(summary.creditSum(), creditSum));
    }

    if (summary.debitSum() != null && summary.debitSum().compareTo(debitSum) != 0) {
      mismatches.add(
          "debit sum mismatch: expected=%s, actual=%s".formatted(summary.debitSum(), debitSum));
    }

    var opening =
        balances.stream()
            .filter(b -> b.type() == OPEN)
            .map(BankStatementBalance::balance)
            .findFirst();
    var closing =
        balances.stream()
            .filter(b -> b.type() == CLOSE)
            .map(BankStatementBalance::balance)
            .findFirst();

    if (opening.isPresent() && closing.isPresent()) {
      var expectedClosing = opening.get().add(creditSum).subtract(debitSum);
      if (expectedClosing.compareTo(closing.get()) != 0) {
        mismatches.add(
            "balance equation mismatch: expected=%s, actual=%s"
                .formatted(expectedClosing, closing.get()));
      }
    }

    return mismatches;
  }
}
