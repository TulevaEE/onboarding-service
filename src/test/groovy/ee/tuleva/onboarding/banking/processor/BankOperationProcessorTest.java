package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.INTEREST_RECEIVED;
import static ee.tuleva.onboarding.ledger.SystemAccount.INCOMING_PAYMENTS_CLEARING;
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

    processor.processBankOperation(entry, "EE123456789012345678", DEPOSIT_EUR);

    verifyNoInteractions(savingsFundLedger);
  }

  @Test
  void processBankOperation_recordsInterestReceived() {
    var amount = new BigDecimal("5.00");
    var entry = createBankOperationEntry("INTR", amount);

    processor.processBankOperation(entry, "EE123456789012345678", DEPOSIT_EUR);

    verify(savingsFundLedger)
        .recordInterestReceived(eq(amount), any(UUID.class), eq(INCOMING_PAYMENTS_CLEARING));
  }

  @Test
  void processBankOperation_recordsBankFee() {
    var amount = new BigDecimal("-1.00");
    var entry = createBankOperationEntry("FEES", amount);

    processor.processBankOperation(entry, "EE123456789012345678", DEPOSIT_EUR);

    verify(savingsFundLedger)
        .recordBankFee(eq(amount), any(UUID.class), eq(INCOMING_PAYMENTS_CLEARING));
  }

  @Test
  void processBankOperation_recordsBankAdjustment() {
    var amount = new BigDecimal("0.50");
    var entry = createBankOperationEntry("ADJT", amount);

    processor.processBankOperation(entry, "EE123456789012345678", DEPOSIT_EUR);

    verify(savingsFundLedger)
        .recordBankAdjustment(eq(amount), any(UUID.class), eq(INCOMING_PAYMENTS_CLEARING));
  }

  @Test
  void processBankOperation_skipsAlreadyRecordedEntry() {
    var entry = createBankOperationEntry("INTR", new BigDecimal("5.00"));
    when(savingsFundLedger.hasLedgerEntry(any(UUID.class), eq(INTEREST_RECEIVED))).thenReturn(true);

    processor.processBankOperation(entry, "EE123456789012345678", DEPOSIT_EUR);

    verify(savingsFundLedger, never()).recordInterestReceived(any(), any(), any());
  }

  @Test
  void processBankOperation_doesNotRecordUnknownSubFamilyCode() {
    var entry = createBankOperationEntry("UNKN", new BigDecimal("10.00"));

    processor.processBankOperation(entry, "EE123456789012345678", DEPOSIT_EUR);

    verify(savingsFundLedger, never()).recordInterestReceived(any(), any(), any());
    verify(savingsFundLedger, never()).recordBankFee(any(), any(), any());
    verify(savingsFundLedger, never()).recordBankAdjustment(any(), any(), any());
  }

  @Test
  void processBankOperation_handlesNullSubFamilyCode() {
    var entry = createBankOperationEntry(null, new BigDecimal("10.00"));

    processor.processBankOperation(entry, "EE123456789012345678", DEPOSIT_EUR);

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
