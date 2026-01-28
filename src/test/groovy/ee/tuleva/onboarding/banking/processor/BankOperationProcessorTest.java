package ee.tuleva.onboarding.banking.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.banking.statement.TransactionType;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankOperationProcessorTest {

  @Mock SavingsFundLedger savingsFundLedger;

  @InjectMocks BankOperationProcessor processor;

  @Test
  void processBankOperation_skipsEntriesWithCounterparty() {
    var entry = createEntryWithCounterparty();

    processor.processBankOperation(entry, UUID.randomUUID());

    verifyNoInteractions(savingsFundLedger);
  }

  @Test
  void processBankOperation_recordsInterestReceived() {
    var amount = new BigDecimal("5.00");
    var entry = createBankOperationEntry("INTR", amount);

    processor.processBankOperation(entry, UUID.randomUUID());

    verify(savingsFundLedger).recordInterestReceived(eq(amount), any(UUID.class));
  }

  @Test
  void processBankOperation_recordsBankFee() {
    var amount = new BigDecimal("-1.00");
    var entry = createBankOperationEntry("FEES", amount);

    processor.processBankOperation(entry, UUID.randomUUID());

    verify(savingsFundLedger).recordBankFee(eq(amount), any(UUID.class));
  }

  @Test
  void processBankOperation_recordsBankAdjustment() {
    var amount = new BigDecimal("0.50");
    var entry = createBankOperationEntry("ADJT", amount);

    processor.processBankOperation(entry, UUID.randomUUID());

    verify(savingsFundLedger).recordBankAdjustment(eq(amount), any(UUID.class));
  }

  @Test
  void processBankOperation_skipsAlreadyRecordedEntry() {
    var entry = createBankOperationEntry("INTR", new BigDecimal("5.00"));
    when(savingsFundLedger.hasLedgerEntry(any(UUID.class))).thenReturn(true);

    processor.processBankOperation(entry, UUID.randomUUID());

    verify(savingsFundLedger, never()).recordInterestReceived(any(), any());
  }

  @Test
  void processBankOperation_doesNotRecordUnknownSubFamilyCode() {
    var entry = createBankOperationEntry("UNKN", new BigDecimal("10.00"));

    processor.processBankOperation(entry, UUID.randomUUID());

    verify(savingsFundLedger, never()).recordInterestReceived(any(), any());
    verify(savingsFundLedger, never()).recordBankFee(any(), any());
    verify(savingsFundLedger, never()).recordBankAdjustment(any(), any());
  }

  @Test
  void processBankOperation_handlesNullSubFamilyCode() {
    var entry = createBankOperationEntry(null, new BigDecimal("10.00"));

    processor.processBankOperation(entry, UUID.randomUUID());

    verifyNoInteractions(savingsFundLedger);
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
