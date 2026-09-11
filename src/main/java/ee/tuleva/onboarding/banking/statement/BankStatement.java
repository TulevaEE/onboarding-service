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
    requireIntegrity(entries, summary, balances, account);

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
    requireIntegrity(entries, summary, balances, account);

    return new BankStatement(HISTORIC_STATEMENT, account, balances, entries);
  }

  private static void requireIntegrity(
      List<BankStatementEntry> entries,
      @Nullable TransactionSummary summary,
      List<BankStatementBalance> balances,
      BankStatementAccount account) {
    var mismatches = validateEntries(entries, summary, balances);
    if (!mismatches.isEmpty()) {
      throw new BankStatementParseException(
          "Bank statement integrity check failed: account=%s, mismatches=%s"
              .formatted(account.iban(), mismatches));
    }
  }

  static List<String> validateEntries(
      List<BankStatementEntry> entries,
      @Nullable TransactionSummary summary,
      List<BankStatementBalance> balances) {

    var mismatches = new ArrayList<String>();
    if (summary == null) return mismatches;

    var creditSum = creditEntrySum(entries);
    var debitSum = debitEntrySum(entries);

    addIfPresent(mismatches, checkTotalCountMismatch(summary, entries.size()));
    addIfPresent(mismatches, checkCreditCountMismatch(summary, creditEntryCount(entries)));
    addIfPresent(mismatches, checkDebitCountMismatch(summary, debitEntryCount(entries)));
    addIfPresent(mismatches, checkCreditSumMismatch(summary, creditSum));
    addIfPresent(mismatches, checkDebitSumMismatch(summary, debitSum));
    addIfPresent(mismatches, checkBalanceEquationMismatch(balances, creditSum, debitSum));

    return mismatches;
  }

  private static void addIfPresent(List<String> mismatches, @Nullable String mismatch) {
    if (mismatch != null) {
      mismatches.add(mismatch);
    }
  }

  private static long creditEntryCount(List<BankStatementEntry> entries) {
    return entries.stream().filter(e -> e.amount().signum() > 0).count();
  }

  private static long debitEntryCount(List<BankStatementEntry> entries) {
    return entries.stream().filter(e -> e.amount().signum() < 0).count();
  }

  private static BigDecimal creditEntrySum(List<BankStatementEntry> entries) {
    return entries.stream()
        .filter(e -> e.amount().signum() > 0)
        .map(BankStatementEntry::amount)
        .reduce(ZERO, BigDecimal::add);
  }

  private static BigDecimal debitEntrySum(List<BankStatementEntry> entries) {
    return entries.stream()
        .filter(e -> e.amount().signum() < 0)
        .map(e -> e.amount().abs())
        .reduce(ZERO, BigDecimal::add);
  }

  @Nullable
  private static String checkTotalCountMismatch(TransactionSummary summary, int actualCount) {
    if (summary.totalCount() == null || actualCount == Integer.parseInt(summary.totalCount())) {
      return null;
    }
    return "total entry count mismatch: expected=%s, actual=%d"
        .formatted(summary.totalCount(), actualCount);
  }

  @Nullable
  private static String checkCreditCountMismatch(TransactionSummary summary, long actualCount) {
    if (summary.creditCount() == null || actualCount == Integer.parseInt(summary.creditCount())) {
      return null;
    }
    return "credit entry count mismatch: expected=%s, actual=%d"
        .formatted(summary.creditCount(), actualCount);
  }

  @Nullable
  private static String checkDebitCountMismatch(TransactionSummary summary, long actualCount) {
    if (summary.debitCount() == null || actualCount == Integer.parseInt(summary.debitCount())) {
      return null;
    }
    return "debit entry count mismatch: expected=%s, actual=%d"
        .formatted(summary.debitCount(), actualCount);
  }

  @Nullable
  private static String checkCreditSumMismatch(TransactionSummary summary, BigDecimal actualSum) {
    if (summary.creditSum() == null || summary.creditSum().compareTo(actualSum) == 0) {
      return null;
    }
    return "credit sum mismatch: expected=%s, actual=%s".formatted(summary.creditSum(), actualSum);
  }

  @Nullable
  private static String checkDebitSumMismatch(TransactionSummary summary, BigDecimal actualSum) {
    if (summary.debitSum() == null || summary.debitSum().compareTo(actualSum) == 0) {
      return null;
    }
    return "debit sum mismatch: expected=%s, actual=%s".formatted(summary.debitSum(), actualSum);
  }

  @Nullable
  private static String checkBalanceEquationMismatch(
      List<BankStatementBalance> balances, BigDecimal creditSum, BigDecimal debitSum) {
    var opening = findOpeningBalance(balances);
    var closing = findClosingBalance(balances);
    if (opening == null || closing == null) {
      return null;
    }
    var expectedClosing = opening.add(creditSum).subtract(debitSum);
    if (expectedClosing.compareTo(closing) == 0) {
      return null;
    }
    return "balance equation mismatch: expected=%s, actual=%s".formatted(expectedClosing, closing);
  }

  @Nullable
  private static BigDecimal findOpeningBalance(List<BankStatementBalance> balances) {
    return balances.stream()
        .filter(b -> b.type() == OPEN)
        .map(BankStatementBalance::balance)
        .findFirst()
        .orElse(null);
  }

  @Nullable
  private static BigDecimal findClosingBalance(List<BankStatementBalance> balances) {
    return balances.stream()
        .filter(b -> b.type() == CLOSE)
        .map(BankStatementBalance::balance)
        .findFirst()
        .orElse(null);
  }
}
