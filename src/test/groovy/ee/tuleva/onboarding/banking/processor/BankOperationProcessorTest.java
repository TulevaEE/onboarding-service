package ee.tuleva.onboarding.banking.processor;

import static org.assertj.core.api.Assertions.assertThatCode;

import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.banking.statement.TransactionType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BankOperationProcessorTest {

  BankOperationProcessor processor = new BankOperationProcessor();

  @Test
  void processBankOperation_skipsEntriesWithCounterparty() {
    var entry = createEntryWithCounterparty();

    assertThatCode(() -> processor.processBankOperation(entry, UUID.randomUUID()))
        .doesNotThrowAnyException();
  }

  @Test
  void processBankOperation_handlesInterestEntry() {
    var entry = createBankOperationEntry("INTR", new BigDecimal("5.00"));

    assertThatCode(() -> processor.processBankOperation(entry, UUID.randomUUID()))
        .doesNotThrowAnyException();
  }

  @Test
  void processBankOperation_handlesFeeEntry() {
    var entry = createBankOperationEntry("FEES", new BigDecimal("-1.00"));

    assertThatCode(() -> processor.processBankOperation(entry, UUID.randomUUID()))
        .doesNotThrowAnyException();
  }

  @Test
  void processBankOperation_handlesFeeAdjustmentEntry() {
    var entry = createBankOperationEntry("ADJT", new BigDecimal("0.50"));

    assertThatCode(() -> processor.processBankOperation(entry, UUID.randomUUID()))
        .doesNotThrowAnyException();
  }

  @Test
  void processBankOperation_handlesUnknownSubFamilyCode() {
    var entry = createBankOperationEntry("UNKN", new BigDecimal("10.00"));

    assertThatCode(() -> processor.processBankOperation(entry, UUID.randomUUID()))
        .doesNotThrowAnyException();
  }

  @Test
  void processBankOperation_handlesNullSubFamilyCode() {
    var entry = createBankOperationEntry(null, new BigDecimal("10.00"));

    assertThatCode(() -> processor.processBankOperation(entry, UUID.randomUUID()))
        .doesNotThrowAnyException();
  }

  private BankStatementEntry createEntryWithCounterparty() {
    var counterparty = new BankStatementEntry.CounterPartyDetails("Test", "EE123", null);
    return new BankStatementEntry(
        counterparty,
        new BigDecimal("100.00"),
        "EUR",
        TransactionType.CREDIT,
        "Test payment",
        "test-ref",
        null,
        null);
  }

  private BankStatementEntry createBankOperationEntry(String subFamilyCode, BigDecimal amount) {
    return new BankStatementEntry(
        null,
        amount,
        "EUR",
        amount.compareTo(BigDecimal.ZERO) >= 0 ? TransactionType.CREDIT : TransactionType.DEBIT,
        "Bank operation",
        "bank-op-ref",
        null,
        subFamilyCode);
  }
}
