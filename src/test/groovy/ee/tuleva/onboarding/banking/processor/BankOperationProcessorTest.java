package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.INTEREST_RECEIVED;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
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
  @Mock TradeSettlementParser tradeSettlementParser;

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
  void processBankOperation_recordsBankFeeForCommissionCode() {
    var amount = new BigDecimal("-0.48");
    var entry = createBankOperationEntry("COMM", amount);

    processor.processBankOperation(entry, "EE123456789012345678", DEPOSIT_EUR);

    verify(savingsFundLedger)
        .recordBankFee(eq(amount), any(UUID.class), eq(INCOMING_PAYMENTS_CLEARING));
  }

  @Test
  void processBankOperation_handlesNullSubFamilyCode() {
    var entry = createBankOperationEntry(null, new BigDecimal("10.00"));

    processor.processBankOperation(entry, "EE123456789012345678", DEPOSIT_EUR);

    verifyNoInteractions(savingsFundLedger);
  }

  @Test
  void processBankOperation_recordsTradeSettlement() {
    var amount = new BigDecimal("-209080.26");
    var remittanceInfo = "DLA0553690/EJAP GY/11704/17.864/Buy/ Euroclear, ABNCNL2AXXX, 14448";
    var entry = createBankOperationEntry("TRAD", amount, remittanceInfo);
    var fundTicker =
        ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker.BNP_JAPAN_ESG_FILTERED;
    var tradeInfo =
        new TradeSettlementParser.TradeSettlementInfo(fundTicker, new BigDecimal("11704"));

    when(tradeSettlementParser.parse(remittanceInfo)).thenReturn(java.util.Optional.of(tradeInfo));

    processor.processBankOperation(entry, "EE123456789012345678", FUND_INVESTMENT_EUR);

    verify(savingsFundLedger)
        .recordTradeSettlement(
            eq(amount),
            eq(new BigDecimal("11704.00000")),
            any(UUID.class),
            eq(FUND_INVESTMENT_CASH_CLEARING),
            eq("LU1291102447"),
            eq("EJAP"),
            eq("BNP Paribas Easy MSCI Japan ESG Filtered"));
  }

  @Test
  void processBankOperation_skipsTradeSettlementWithUnknownTicker() {
    var amount = new BigDecimal("-100000.00");
    var remittanceInfo = "DLA0553690/ZZZZ GY/11704/17.864/Buy/ Euroclear, ABNCNL2AXXX, 14448";
    var entry = createBankOperationEntry("TRAD", amount, remittanceInfo);

    when(tradeSettlementParser.parse(remittanceInfo)).thenReturn(java.util.Optional.empty());

    processor.processBankOperation(entry, "EE123456789012345678", FUND_INVESTMENT_EUR);

    verify(savingsFundLedger, never())
        .recordTradeSettlement(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void processBankOperation_recordsTradeSettlementForSubsCode() {
    var amount = new BigDecimal("-1071209.00");
    var remittanceInfo =
        "DLA0544429/BDWTEIA/31426.66/34.085995776/Buy/ BlackRock Asset Management Ireland Ltd";
    var entry = createBankOperationEntry("SUBS", amount, remittanceInfo);
    var fundTicker =
        ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker
            .ISHARES_DEVELOPED_WORLD_ESG_SCREENED;
    var tradeInfo =
        new TradeSettlementParser.TradeSettlementInfo(fundTicker, new BigDecimal("31426.66"));

    when(tradeSettlementParser.parse(remittanceInfo)).thenReturn(java.util.Optional.of(tradeInfo));

    processor.processBankOperation(entry, "EE123456789012345678", FUND_INVESTMENT_EUR);

    verify(savingsFundLedger)
        .recordTradeSettlement(
            eq(amount),
            eq(new BigDecimal("31426.66000")),
            any(UUID.class),
            eq(FUND_INVESTMENT_CASH_CLEARING),
            eq("IE00BFG1TM61"),
            eq("0P000152G5"),
            eq("iShares Developed World ESG Screened Index Fund"));
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
        null,
        null);
  }

  private BankStatementEntry createBankOperationEntry(String subFamilyCode, BigDecimal amount) {
    return createBankOperationEntry(subFamilyCode, amount, "Bank operation");
  }

  private BankStatementEntry createBankOperationEntry(
      String subFamilyCode, BigDecimal amount, String remittanceInformation) {
    return new BankStatementEntry(
        null,
        amount,
        "EUR",
        amount.compareTo(BigDecimal.ZERO) >= 0 ? TransactionType.CREDIT : TransactionType.DEBIT,
        remittanceInformation,
        "bank-op-ref",
        null,
        subFamilyCode,
        null);
  }
}
