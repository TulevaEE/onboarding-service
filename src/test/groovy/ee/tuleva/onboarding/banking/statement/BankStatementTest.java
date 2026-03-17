package ee.tuleva.onboarding.banking.statement;

import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.CLOSE;
import static ee.tuleva.onboarding.banking.statement.BankStatementBalance.StatementBalanceType.OPEN;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
