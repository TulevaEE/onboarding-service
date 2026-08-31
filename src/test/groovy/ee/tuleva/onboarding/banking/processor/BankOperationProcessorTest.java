package ee.tuleva.onboarding.banking.processor;

import static ee.tuleva.onboarding.banking.BankAccountType.DEPOSIT_EUR;
import static ee.tuleva.onboarding.banking.BankAccountType.FUND_INVESTMENT_EUR;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.INTEREST_RECEIVED;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.MANAGEMENT_FEE_REBATE;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_INVESTMENT_CASH_CLEARING;
import static ee.tuleva.onboarding.ledger.SystemAccount.INCOMING_PAYMENTS_CLEARING;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.banking.statement.TransactionType;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.LedgerTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankOperationProcessorTest {

  private static final BankAccount DEPOSIT_ACCOUNT =
      new BankAccount("EE123456789012345678", DEPOSIT_EUR, TKF100, "gw-test");
  private static final BankAccount FUND_INVESTMENT_ACCOUNT =
      new BankAccount("EE123456789012345678", FUND_INVESTMENT_EUR, TKF100, "gw-test");

  @Mock FundBankLedger fundBankLedger;
  @Mock TradeSettlementParser tradeSettlementParser;

  @InjectMocks BankOperationProcessor processor;

  @Test
  void processBankOperation_skipsEntriesWithCounterparty() {
    var entry = createEntryWithCounterparty();

    processor.processBankOperation(entry, DEPOSIT_ACCOUNT);

    verifyNoInteractions(fundBankLedger);
  }

  @Test
  void processBankOperation_recordsInterestReceivedWithBookingDate() {
    var amount = new BigDecimal("5.00");
    var entry = createBankOperationEntry("INTR", amount);

    processor.processBankOperation(entry, DEPOSIT_ACCOUNT);

    verify(fundBankLedger)
        .recordInterestReceived(
            eq(TKF100),
            eq(amount),
            any(UUID.class),
            eq(INCOMING_PAYMENTS_CLEARING),
            eq(LocalDate.of(2025, 10, 1)));
  }

  @Test
  void processBankOperation_recordsBankFeeWithBookingDate() {
    var amount = new BigDecimal("-1.00");
    var entry = createBankOperationEntry("FEES", amount);

    processor.processBankOperation(entry, DEPOSIT_ACCOUNT);

    verify(fundBankLedger)
        .recordBankFee(
            eq(TKF100),
            eq(amount),
            any(UUID.class),
            eq(INCOMING_PAYMENTS_CLEARING),
            eq(LocalDate.of(2025, 10, 1)));
  }

  @Test
  void processBankOperation_recordsBankAdjustmentWithBookingDate() {
    var amount = new BigDecimal("0.50");
    var entry = createBankOperationEntry("ADJT", amount);

    processor.processBankOperation(entry, DEPOSIT_ACCOUNT);

    verify(fundBankLedger)
        .recordBankAdjustment(
            eq(TKF100),
            eq(amount),
            any(UUID.class),
            eq(INCOMING_PAYMENTS_CLEARING),
            eq(LocalDate.of(2025, 10, 1)));
  }

  @Test
  void processBankOperation_skipsAlreadyRecordedEntry() {
    var entry = createBankOperationEntry("INTR", new BigDecimal("5.00"));
    when(fundBankLedger.hasLedgerEntry(any(UUID.class), eq(INTEREST_RECEIVED))).thenReturn(true);

    processor.processBankOperation(entry, DEPOSIT_ACCOUNT);

    verify(fundBankLedger, never()).recordInterestReceived(any(), any(), any(), any(), any());
  }

  @Test
  void processBankOperation_parksUnknownSubFamilyCodeInSuspense() {
    var entry = createBankOperationEntry("UNKN", new BigDecimal("10.00"));

    processor.processBankOperation(entry, DEPOSIT_ACCOUNT);

    verify(fundBankLedger)
        .recordUnclassifiedBankEntry(
            eq(TKF100),
            eq(new BigDecimal("10.00")),
            any(UUID.class),
            eq(INCOMING_PAYMENTS_CLEARING),
            eq(LocalDate.of(2025, 10, 1)),
            eq(new FundBankLedger.UnclassifiedEntryDetails(null, null, "Bank operation", "UNKN")));
    verify(fundBankLedger, never()).recordBankAdjustment(any(), any(), any(), any(), any());
  }

  @Test
  void processBankOperation_skipsAlreadyParkedUnknownSubFamilyCode() {
    var entry = createBankOperationEntry("UNKN", new BigDecimal("10.00"));
    when(fundBankLedger.hasLedgerEntry(
            any(UUID.class), eq(LedgerTransaction.TransactionType.UNCLASSIFIED_BANK_ENTRY)))
        .thenReturn(true);

    processor.processBankOperation(entry, DEPOSIT_ACCOUNT);

    verify(fundBankLedger, never())
        .recordUnclassifiedBankEntry(any(), any(), any(), any(), any(), any());
  }

  @Test
  void processBankOperation_recordsBankAdjustmentForOtherCode() {
    var amount = new BigDecimal("262.53");
    var entry =
        createBankOperationEntry(
            "OTHR", amount, "Penalty CRED/VP68168/MGTCBEBEECL/2026-02/262.53 EUR");

    processor.processBankOperation(entry, FUND_INVESTMENT_ACCOUNT);

    verify(fundBankLedger)
        .recordBankAdjustment(
            eq(TKF100),
            eq(amount),
            any(UUID.class),
            eq(FUND_INVESTMENT_CASH_CLEARING),
            eq(LocalDate.of(2025, 10, 1)));
  }

  @Test
  void processBankOperation_recordsManagementFeeRebateForBookKickback() {
    var amount = new BigDecimal("4370.58");
    var entry = createBankOperationEntry("BOOK", amount, "Management fee kickback VP68168 02/2026");

    processor.processBankOperation(entry, FUND_INVESTMENT_ACCOUNT);

    verify(fundBankLedger)
        .recordManagementFeeRebate(
            eq(TKF100),
            eq(amount),
            any(UUID.class),
            eq(FUND_INVESTMENT_CASH_CLEARING),
            eq(LocalDate.of(2025, 10, 1)),
            eq("Management fee kickback VP68168 02/2026"));
  }

  @Test
  void processBankOperation_parksBookTransferWithoutKickbackInSuspense() {
    var entry = createBankOperationEntry("BOOK", new BigDecimal("100.00"), "Internal transfer");

    processor.processBankOperation(entry, FUND_INVESTMENT_ACCOUNT);

    verify(fundBankLedger)
        .recordUnclassifiedBankEntry(
            eq(TKF100),
            eq(new BigDecimal("100.00")),
            any(UUID.class),
            eq(FUND_INVESTMENT_CASH_CLEARING),
            eq(LocalDate.of(2025, 10, 1)),
            eq(
                new FundBankLedger.UnclassifiedEntryDetails(
                    null, null, "Internal transfer", "BOOK")));
  }

  @Test
  void processBankOperation_skipsAlreadyRecordedManagementFeeRebate() {
    var entry =
        createBankOperationEntry(
            "BOOK", new BigDecimal("4370.58"), "Management fee kickback VP68168 02/2026");
    when(fundBankLedger.hasLedgerEntry(any(UUID.class), eq(MANAGEMENT_FEE_REBATE)))
        .thenReturn(true);

    processor.processBankOperation(entry, FUND_INVESTMENT_ACCOUNT);

    verify(fundBankLedger, never())
        .recordManagementFeeRebate(any(), any(), any(), any(), any(), any());
  }

  @Test
  void processBankOperation_recordsBankFeeForCommissionCode() {
    var amount = new BigDecimal("-0.48");
    var entry = createBankOperationEntry("COMM", amount);

    processor.processBankOperation(entry, DEPOSIT_ACCOUNT);

    verify(fundBankLedger)
        .recordBankFee(
            eq(TKF100),
            eq(amount),
            any(UUID.class),
            eq(INCOMING_PAYMENTS_CLEARING),
            eq(LocalDate.of(2025, 10, 1)));
  }

  @Test
  void processBankOperation_parksNullSubFamilyCodeInSuspense() {
    var entry = createBankOperationEntry(null, new BigDecimal("10.00"));

    processor.processBankOperation(entry, DEPOSIT_ACCOUNT);

    verify(fundBankLedger)
        .recordUnclassifiedBankEntry(
            eq(TKF100),
            eq(new BigDecimal("10.00")),
            any(UUID.class),
            eq(INCOMING_PAYMENTS_CLEARING),
            eq(LocalDate.of(2025, 10, 1)),
            eq(new FundBankLedger.UnclassifiedEntryDetails(null, null, "Bank operation", null)));
  }

  @Test
  void processBankOperation_recordsTradeSettlementWithBookingDate() {
    var amount = new BigDecimal("-209080.26");
    var remittanceInfo = "DLA0553690/EJAP GY/11704/17.864/Buy/ Euroclear, ABNCNL2AXXX, 14448";
    var entry = createBankOperationEntry("TRAD", amount, remittanceInfo);
    var tradeInfo =
        new TradeSettlementParser.TradeSettlementInfo(
            "LU1291102447",
            "EJAP",
            "BNP Paribas Easy MSCI Japan Min TE UCITS ETF",
            new BigDecimal("11704"));

    when(tradeSettlementParser.parse(remittanceInfo)).thenReturn(java.util.Optional.of(tradeInfo));

    processor.processBankOperation(entry, FUND_INVESTMENT_ACCOUNT);

    verify(fundBankLedger)
        .recordTradeSettlement(
            eq(TKF100),
            eq(amount),
            eq(new BigDecimal("11704.00000")),
            any(UUID.class),
            eq(FUND_INVESTMENT_CASH_CLEARING),
            eq("LU1291102447"),
            eq("EJAP"),
            eq("BNP Paribas Easy MSCI Japan Min TE UCITS ETF"),
            eq(LocalDate.of(2025, 10, 1)));
  }

  @Test
  void processBankOperation_parksUnknownTickerTradeInSuspense() {
    var amount = new BigDecimal("-100000.00");
    var remittanceInfo = "DLA0553690/ZZZZ GY/11704/17.864/Buy/ Euroclear, ABNCNL2AXXX, 14448";
    var entry = createBankOperationEntry("TRAD", amount, remittanceInfo);

    when(tradeSettlementParser.parse(remittanceInfo)).thenReturn(java.util.Optional.empty());

    processor.processBankOperation(entry, FUND_INVESTMENT_ACCOUNT);

    verify(fundBankLedger, never())
        .recordTradeSettlement(any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(fundBankLedger)
        .recordUnclassifiedBankEntry(
            eq(TKF100),
            eq(new BigDecimal("-100000.00")),
            any(UUID.class),
            eq(FUND_INVESTMENT_CASH_CLEARING),
            eq(LocalDate.of(2025, 10, 1)),
            eq(new FundBankLedger.UnclassifiedEntryDetails(null, null, remittanceInfo, "TRAD")));
  }

  @Test
  void processBankOperation_recordsTradeSettlementForSubsCode() {
    var amount = new BigDecimal("-1071209.00");
    var remittanceInfo =
        "DLA0544429/BDWTEIA/31426.66/34.085995776/Buy/ BlackRock Asset Management Ireland Ltd";
    var entry = createBankOperationEntry("SUBS", amount, remittanceInfo);
    var tradeInfo =
        new TradeSettlementParser.TradeSettlementInfo(
            "IE00BFG1TM61",
            "0P000152G5",
            "iShares Developed World Screened Index Fund",
            new BigDecimal("31426.66"));

    when(tradeSettlementParser.parse(remittanceInfo)).thenReturn(java.util.Optional.of(tradeInfo));

    processor.processBankOperation(entry, FUND_INVESTMENT_ACCOUNT);

    verify(fundBankLedger)
        .recordTradeSettlement(
            eq(TKF100),
            eq(amount),
            eq(new BigDecimal("31426.66000")),
            any(UUID.class),
            eq(FUND_INVESTMENT_CASH_CLEARING),
            eq("IE00BFG1TM61"),
            eq("0P000152G5"),
            eq("iShares Developed World Screened Index Fund"),
            eq(LocalDate.of(2025, 10, 1)));
  }

  @Test
  void processBankOperation_recordsZeroAmountSettlementWithoutNegatingUnits() {
    var amount = BigDecimal.ZERO;
    var remittanceInfo = "DLA0553690/EJAP GY/11704/17.864/Buy/ Euroclear, ABNCNL2AXXX, 14448";
    var entry = createBankOperationEntry("TRAD", amount, remittanceInfo);
    var tradeInfo =
        new TradeSettlementParser.TradeSettlementInfo(
            "LU1291102447",
            "EJAP",
            "BNP Paribas Easy MSCI Japan Min TE UCITS ETF",
            new BigDecimal("11704"));

    when(tradeSettlementParser.parse(remittanceInfo)).thenReturn(java.util.Optional.of(tradeInfo));

    processor.processBankOperation(entry, FUND_INVESTMENT_ACCOUNT);

    verify(fundBankLedger)
        .recordTradeSettlement(
            eq(TKF100),
            eq(amount.setScale(2, java.math.RoundingMode.HALF_UP)),
            eq(new BigDecimal("11704.00000")),
            any(UUID.class),
            eq(FUND_INVESTMENT_CASH_CLEARING),
            eq("LU1291102447"),
            eq("EJAP"),
            eq("BNP Paribas Easy MSCI Japan Min TE UCITS ETF"),
            eq(LocalDate.of(2025, 10, 1)));
  }

  @Test
  void processBankOperation_recordsSellSettlementWithNegativeUnits() {
    var amount = new BigDecimal("50000.00");
    var remittanceInfo =
        "DLA0553691/BDWTEIA/1450.25/34.477/Sell/ BlackRock Asset Management Ireland Ltd";
    var entry = createBankOperationEntry("SUBS", amount, remittanceInfo);
    var tradeInfo =
        new TradeSettlementParser.TradeSettlementInfo(
            "IE00BFG1TM61",
            "0P000152G5",
            "iShares Developed World Screened Index Fund",
            new BigDecimal("1450.25"));

    when(tradeSettlementParser.parse(remittanceInfo)).thenReturn(java.util.Optional.of(tradeInfo));

    processor.processBankOperation(entry, FUND_INVESTMENT_ACCOUNT);

    verify(fundBankLedger)
        .recordTradeSettlement(
            eq(TKF100),
            eq(amount),
            eq(new BigDecimal("-1450.25000")),
            any(UUID.class),
            eq(FUND_INVESTMENT_CASH_CLEARING),
            eq("IE00BFG1TM61"),
            eq("0P000152G5"),
            eq("iShares Developed World Screened Index Fund"),
            eq(LocalDate.of(2025, 10, 1)));
  }

  @Test
  void processBankOperation_failsOnMissingExternalId() {
    var entry =
        new BankStatementEntry(
            null,
            new BigDecimal("10.00"),
            "EUR",
            TransactionType.CREDIT,
            "tundmatu",
            null,
            null,
            "OTHR",
            Instant.parse("2025-10-01T20:59:59.999999Z"));

    assertThatThrownBy(() -> processor.processBankOperation(entry, DEPOSIT_ACCOUNT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("external id");
    verifyNoInteractions(fundBankLedger);
  }

  @Test
  void processBankOperation_failsOnMissingBookingTime() {
    var entry =
        new BankStatementEntry(
            null,
            new BigDecimal("5.00"),
            "EUR",
            TransactionType.CREDIT,
            "intress",
            "bank-op-ref",
            null,
            "INTR",
            null);

    assertThatThrownBy(() -> processor.processBankOperation(entry, DEPOSIT_ACCOUNT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("booking time");
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
        Instant.parse("2025-10-01T20:59:59.999999Z"));
  }
}
