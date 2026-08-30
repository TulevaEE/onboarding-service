package ee.tuleva.onboarding.banking.statement;

import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.CLOSE;
import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.OPEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.banking.iso20022.camt052.NumberAndSumOfTransactions1;
import ee.tuleva.onboarding.banking.iso20022.camt052.NumberAndSumOfTransactions2;
import ee.tuleva.onboarding.banking.iso20022.camt052.TotalTransactions2;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BankStatementTest {

  private static final LocalDate DATE = LocalDate.of(2026, 3, 13);

  @Test
  void validateEntries_detectsEntryCountMismatch() {
    var entries = List.of(creditEntry("100.00"), creditEntry("200.00"));
    var summary = new BankStatement.TransactionSummary("3", null, null, null, null, null);

    var mismatches = BankStatement.validateEntries(entries, summary, List.of());

    assertThat(mismatches)
        .singleElement()
        .asString()
        .contains("total entry count")
        .contains("expected=3")
        .contains("actual=2");
  }

  @Test
  void validateEntries_detectsCreditCountMismatch() {
    var entries = List.of(creditEntry("100.00"));
    var summary = new BankStatement.TransactionSummary(null, null, "2", null, null, null);

    var mismatches = BankStatement.validateEntries(entries, summary, List.of());

    assertThat(mismatches)
        .singleElement()
        .asString()
        .contains("credit entry count")
        .contains("expected=2")
        .contains("actual=1");
  }

  @Test
  void validateEntries_detectsDebitCountMismatch() {
    var entries = List.of(debitEntry("500.00"));
    var summary = new BankStatement.TransactionSummary(null, null, null, null, "2", null);

    var mismatches = BankStatement.validateEntries(entries, summary, List.of());

    assertThat(mismatches)
        .singleElement()
        .asString()
        .contains("debit entry count")
        .contains("expected=2")
        .contains("actual=1");
  }

  @Test
  void validateEntries_detectsCreditSumMismatch() {
    var entries = List.of(creditEntry("5000.25"));
    var summary =
        new BankStatement.TransactionSummary(
            null, null, null, new BigDecimal("10000.00"), null, null);

    var mismatches = BankStatement.validateEntries(entries, summary, List.of());

    assertThat(mismatches)
        .singleElement()
        .asString()
        .contains("credit sum")
        .contains("expected=10000.00")
        .contains("actual=5000.25");
  }

  @Test
  void validateEntries_detectsDebitSumMismatch() {
    var entries = List.of(debitEntry("500.00"));
    var summary =
        new BankStatement.TransactionSummary(
            null, null, null, null, null, new BigDecimal("1000.00"));

    var mismatches = BankStatement.validateEntries(entries, summary, List.of());

    assertThat(mismatches)
        .singleElement()
        .asString()
        .contains("debit sum")
        .contains("expected=1000.00")
        .contains("actual=500.00");
  }

  @Test
  void validateEntries_detectsBalanceEquationFailure() {
    var entries = List.of(creditEntry("5000.00"), debitEntry("1000.00"));
    var summary =
        new BankStatement.TransactionSummary(
            "2", null, "1", new BigDecimal("5000.00"), "1", new BigDecimal("1000.00"));
    var balances =
        List.of(
            new BankStatementBalance(OPEN, DATE, new BigDecimal("50000.00")),
            new BankStatementBalance(CLOSE, DATE, new BigDecimal("49000.00")));

    var mismatches = BankStatement.validateEntries(entries, summary, balances);

    assertThat(mismatches)
        .singleElement()
        .asString()
        .contains("balance equation")
        .contains("expected=54000.00")
        .contains("actual=49000.00");
  }

  @Test
  void validateEntries_noErrorWhenAllChecksPass() {
    var entries = List.of(creditEntry("23386.35"), debitEntry("28623.54"));
    var summary =
        new BankStatement.TransactionSummary(
            "2", null, "1", new BigDecimal("23386.35"), "1", new BigDecimal("28623.54"));
    var balances =
        List.of(
            new BankStatementBalance(OPEN, DATE, new BigDecimal("42667.03")),
            new BankStatementBalance(CLOSE, DATE, new BigDecimal("37429.84")));

    var mismatches = BankStatement.validateEntries(entries, summary, balances);

    assertThat(mismatches).isEmpty();
  }

  @Test
  void validateEntries_skipsValidationWhenNoSummary() {
    var entries = List.of(creditEntry("100.00"));

    var mismatches = BankStatement.validateEntries(entries, null, List.of());

    assertThat(mismatches).isEmpty();
  }

  @Test
  void validateEntries_skipsBalanceEquationWhenNoOpenOrCloseBalance() {
    var entries = List.of(creditEntry("100.00"));
    var summary =
        new BankStatement.TransactionSummary("1", null, "1", new BigDecimal("100.00"), null, null);
    var balances = List.of(new BankStatementBalance(OPEN, DATE, new BigDecimal("50000.00")));

    var mismatches = BankStatement.validateEntries(entries, summary, balances);

    assertThat(mismatches).isEmpty();
  }

  @Test
  void validateEntries_zeroAmountEntryCountsAsNeitherCreditNorDebit() {
    var entries = List.of(zeroAmountEntry());
    var summary =
        new BankStatement.TransactionSummary("1", null, "0", ZERO_AMOUNT, "0", ZERO_AMOUNT);

    var mismatches = BankStatement.validateEntries(entries, summary, List.of());

    assertThat(mismatches).isEmpty();
  }

  @Test
  void validateEntries_locatesOpeningBalanceByTypeRegardlessOfListOrder() {
    var entries = List.of(creditEntry("1000.00"), debitEntry("500.00"));
    var summary =
        new BankStatement.TransactionSummary(
            "2", null, "1", new BigDecimal("1000.00"), "1", new BigDecimal("500.00"));
    // CLOSE balance listed before OPEN: a filter that ignores balance type would pick this one.
    var balances =
        List.of(
            new BankStatementBalance(CLOSE, DATE, new BigDecimal("10500.00")),
            new BankStatementBalance(OPEN, DATE, new BigDecimal("10000.00")));

    var mismatches = BankStatement.validateEntries(entries, summary, balances);

    assertThat(mismatches).isEmpty();
  }

  @Test
  void from_accountReport_throwsWhenIntegrityCheckFails() {
    var account = Camt052Fixtures.account("EE001234567890123456", "Acme OÜ", List.of("10060701"));
    var entries = List.of(Camt052Fixtures.creditEntry("100.00"));
    // Summary claims zero entries, but one entry is present: total entry count mismatch.
    var summary = new TotalTransactions2();
    var ttlNtries = new NumberAndSumOfTransactions2();
    ttlNtries.setNbOfNtries("0");
    summary.setTtlNtries(ttlNtries);
    var report =
        Camt052Fixtures.accountReport(
            account, List.of(), entries, summary, ZonedDateTime.now(ZoneId.of("Europe/Tallinn")));

    assertThatThrownBy(() -> BankStatement.from(report, ZoneId.of("Europe/Tallinn")))
        .isInstanceOf(BankStatementParseException.class);
  }

  @Nested
  class TransactionSummaryFromCamt052 {

    @Test
    void returnsNullForNullInput() {
      assertThat(BankStatement.TransactionSummary.from((TotalTransactions2) null)).isNull();
    }

    @Test
    void mapsAllSubtotalsWhenPresent() {
      var totals = new TotalTransactions2();

      var ttlNtries = new NumberAndSumOfTransactions2();
      ttlNtries.setNbOfNtries("10");
      ttlNtries.setSum(new BigDecimal("1000.00"));
      totals.setTtlNtries(ttlNtries);

      var ttlCdtNtries = new NumberAndSumOfTransactions1();
      ttlCdtNtries.setNbOfNtries("6");
      ttlCdtNtries.setSum(new BigDecimal("600.00"));
      totals.setTtlCdtNtries(ttlCdtNtries);

      var ttlDbtNtries = new NumberAndSumOfTransactions1();
      ttlDbtNtries.setNbOfNtries("4");
      ttlDbtNtries.setSum(new BigDecimal("400.00"));
      totals.setTtlDbtNtries(ttlDbtNtries);

      var result = BankStatement.TransactionSummary.from(totals);

      assertThat(result)
          .isEqualTo(
              new BankStatement.TransactionSummary(
                  "10",
                  new BigDecimal("1000.00"),
                  "6",
                  new BigDecimal("600.00"),
                  "4",
                  new BigDecimal("400.00")));
    }

    @Test
    void mapsAbsentSubtotalsToNullFields() {
      var totals = new TotalTransactions2();

      var result = BankStatement.TransactionSummary.from(totals);

      assertThat(result)
          .isEqualTo(new BankStatement.TransactionSummary(null, null, null, null, null, null));
    }
  }

  @Nested
  class TransactionSummaryFromCamt053 {

    @Test
    void returnsNullForNullInput() {
      assertThat(
              BankStatement.TransactionSummary.from(
                  (ee.tuleva.onboarding.banking.iso20022.camt053.TotalTransactions2) null))
          .isNull();
    }

    @Test
    void mapsAllSubtotalsWhenPresent() {
      var totals = new ee.tuleva.onboarding.banking.iso20022.camt053.TotalTransactions2();

      var ttlNtries =
          new ee.tuleva.onboarding.banking.iso20022.camt053.NumberAndSumOfTransactions2();
      ttlNtries.setNbOfNtries("10");
      ttlNtries.setSum(new BigDecimal("1000.00"));
      totals.setTtlNtries(ttlNtries);

      var ttlCdtNtries =
          new ee.tuleva.onboarding.banking.iso20022.camt053.NumberAndSumOfTransactions1();
      ttlCdtNtries.setNbOfNtries("6");
      ttlCdtNtries.setSum(new BigDecimal("600.00"));
      totals.setTtlCdtNtries(ttlCdtNtries);

      var ttlDbtNtries =
          new ee.tuleva.onboarding.banking.iso20022.camt053.NumberAndSumOfTransactions1();
      ttlDbtNtries.setNbOfNtries("4");
      ttlDbtNtries.setSum(new BigDecimal("400.00"));
      totals.setTtlDbtNtries(ttlDbtNtries);

      var result = BankStatement.TransactionSummary.from(totals);

      assertThat(result)
          .isEqualTo(
              new BankStatement.TransactionSummary(
                  "10",
                  new BigDecimal("1000.00"),
                  "6",
                  new BigDecimal("600.00"),
                  "4",
                  new BigDecimal("400.00")));
    }

    @Test
    void mapsAbsentSubtotalsToNullFields() {
      var totals = new ee.tuleva.onboarding.banking.iso20022.camt053.TotalTransactions2();

      var result = BankStatement.TransactionSummary.from(totals);

      assertThat(result)
          .isEqualTo(new BankStatement.TransactionSummary(null, null, null, null, null, null));
    }
  }

  private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO;

  private static BankStatementEntry zeroAmountEntry() {
    return new BankStatementEntry(
        null, BigDecimal.ZERO, "EUR", TransactionType.CREDIT, "ref", "ext-0", null, null, null);
  }

  private static BankStatementEntry creditEntry(String amount) {
    return new BankStatementEntry(
        null,
        new BigDecimal(amount),
        "EUR",
        TransactionType.CREDIT,
        "ref",
        "ext-1",
        null,
        null,
        null);
  }

  private static BankStatementEntry debitEntry(String amount) {
    return new BankStatementEntry(
        null,
        new BigDecimal(amount).negate(),
        "EUR",
        TransactionType.DEBIT,
        "ref",
        "ext-1",
        null,
        null,
        null);
  }
}
