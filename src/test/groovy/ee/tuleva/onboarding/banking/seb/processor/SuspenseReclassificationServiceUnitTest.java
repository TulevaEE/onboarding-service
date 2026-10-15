package ee.tuleva.onboarding.banking.seb.processor;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.BANK_FEE;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.TRADE_SETTLEMENT;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNCLASSIFIED_BANK_ENTRY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import ee.tuleva.onboarding.banking.processor.TradeSettlementParser.TradeSettlementInfo;
import ee.tuleva.onboarding.banking.statement.BankStatementEntry;
import ee.tuleva.onboarding.banking.statement.TransactionType;
import ee.tuleva.onboarding.ledger.FundBankLedger;
import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerAccount.AccountPurpose;
import ee.tuleva.onboarding.ledger.LedgerAccount.AccountType;
import ee.tuleva.onboarding.ledger.LedgerAccount.AssetType;
import ee.tuleva.onboarding.ledger.LedgerEntry;
import ee.tuleva.onboarding.ledger.LedgerTransaction;
import ee.tuleva.onboarding.ledger.SystemAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SuspenseReclassificationServiceUnitTest {

  private static final LocalDate BOOKING_DATE = LocalDate.of(2026, 2, 10);

  @Mock private PensionFundEntryClassifier classifier;
  @Mock private FundBankLedger fundBankLedger;

  @InjectMocks private SuspenseReclassificationService service;

  @Test
  void reclassify_locatesTheCashLegByAccountNameEvenWhenListedAfterTheSuspenseLeg() {
    var suspenseAccount =
        ledgerAccount(SystemAccount.UNCLASSIFIED_BANK_ENTRY.getAccountName(TUK75));
    var cashAccount =
        ledgerAccount(SystemAccount.FUND_INVESTMENT_CASH_CLEARING.getAccountName(TUK75));

    var suspenseEntry = ledgerEntry(suspenseAccount, new BigDecimal("-99.99"));
    var cashEntry = ledgerEntry(cashAccount, new BigDecimal("99.99"));

    // Suspense leg listed first, cash leg second: the opposite of production's usual insertion
    // order (FundBankLedger.recordUnclassifiedBankEntry always adds the cash entry first).
    var transaction =
        suspenseTransaction(
            List.of(suspenseEntry, cashEntry),
            Map.of(
                "description",
                "test entry",
                "counterpartyName",
                "Some OÜ",
                "counterpartyIban",
                "EE001234567890123499"));

    given(fundBankLedger.findUnresolvedUnclassifiedEntries(TUK75)).willReturn(List.of(transaction));
    given(classifier.classify(any())).willReturn(new PensionFundEntryClassifier.BankFee());

    service.reclassify(TUK75);

    verify(fundBankLedger)
        .reclassifySuspenseEntry(
            eq(TUK75), eq(new BigDecimal("99.99")), any(), eq(BANK_FEE), any());
  }

  @Test
  void reclassify_leavesATransactionWithNoCashLegInSuspenseAndBooksNothing() {
    var suspenseAccount =
        ledgerAccount(SystemAccount.UNCLASSIFIED_BANK_ENTRY.getAccountName(TUK75));
    var onlyEntry = ledgerEntry(suspenseAccount, new BigDecimal("50.00"));
    var otherEntry = ledgerEntry(suspenseAccount, new BigDecimal("-50.00"));

    var transaction =
        suspenseTransaction(List.of(onlyEntry, otherEntry), Map.of("description", "test entry"));

    given(fundBankLedger.findUnresolvedUnclassifiedEntries(TUK75)).willReturn(List.of(transaction));

    var result = service.reclassify(TUK75);

    assertThat(result).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(0, 1));
    verify(fundBankLedger).findUnresolvedUnclassifiedEntries(TUK75);
    verifyNoMoreInteractions(fundBankLedger);
  }

  @Test
  void reclassify_booksAParkedTradeSettlementAgainstSuspense_onceTheClassifierKnowsItsInstrument() {
    var transaction =
        parkedCash(new BigDecimal("-34448.28"), "DLA0000001/BDWTEIA/1000/34.44828/Buy/", "SUBS");
    given(fundBankLedger.findUnresolvedUnclassifiedEntries(TUK75)).willReturn(List.of(transaction));
    given(
            classifier.classify(
                new BankStatementEntry(
                    null,
                    new BigDecimal("-34448.28"),
                    "EUR",
                    TransactionType.DEBIT,
                    "DLA0000001/BDWTEIA/1000/34.44828/Buy/",
                    "suspense-reclassification",
                    null,
                    "SUBS",
                    null,
                    true)))
        .willReturn(
            new PensionFundEntryClassifier.TradeSettlement(
                new TradeSettlementInfo(
                    "IE00BFG1TM61",
                    "BDWTEIA",
                    "iShares Developed World Screened Index Fund",
                    new BigDecimal("1000"))));

    var result = service.reclassify(TUK75);

    assertThat(result).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(1, 0));
    verify(fundBankLedger)
        .recordTradeSettlement(
            TUK75,
            new BigDecimal("-34448.28"),
            new BigDecimal("1000.00000"),
            transaction.getExternalReference(),
            SystemAccount.UNCLASSIFIED_BANK_ENTRY,
            "IE00BFG1TM61",
            "BDWTEIA",
            "iShares Developed World Screened Index Fund",
            BOOKING_DATE);
  }

  @Test
  void reclassify_booksAParkedSellSettlementWithUnitsLeavingCustody() {
    var transaction =
        parkedCash(new BigDecimal("50000.00"), "DLA0000002/BDWTEIA/1450.25/34.477/Sell/", "TRAD");
    given(fundBankLedger.findUnresolvedUnclassifiedEntries(TUK75)).willReturn(List.of(transaction));
    given(classifier.classify(any()))
        .willReturn(
            new PensionFundEntryClassifier.TradeSettlement(
                new TradeSettlementInfo(
                    "IE00BFG1TM61",
                    "BDWTEIA",
                    "iShares Developed World Screened Index Fund",
                    new BigDecimal("1450.25"))));

    service.reclassify(TUK75);

    verify(fundBankLedger)
        .recordTradeSettlement(
            TUK75,
            new BigDecimal("50000.00"),
            new BigDecimal("-1450.25000"),
            transaction.getExternalReference(),
            SystemAccount.UNCLASSIFIED_BANK_ENTRY,
            "IE00BFG1TM61",
            "BDWTEIA",
            "iShares Developed World Screened Index Fund",
            BOOKING_DATE);
  }

  @Test
  void reclassify_booksParkedInterestAsInterestIncomeAgainstSuspense() {
    var transaction = parkedCash(new BigDecimal("2.00"), "Intress", "INTR");
    given(fundBankLedger.findUnresolvedUnclassifiedEntries(TUK75)).willReturn(List.of(transaction));
    given(classifier.classify(any())).willReturn(new PensionFundEntryClassifier.InterestReceived());

    var result = service.reclassify(TUK75);

    assertThat(result).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(1, 0));
    verify(fundBankLedger)
        .recordInterestReceived(
            TUK75,
            new BigDecimal("2.00"),
            transaction.getExternalReference(),
            SystemAccount.UNCLASSIFIED_BANK_ENTRY,
            BOOKING_DATE);
  }

  @Test
  void reclassify_booksAParkedBankAdjustmentAgainstSuspense() {
    var transaction = parkedCash(new BigDecimal("-3.00"), "Korrigeerimine", "ADJT");
    given(fundBankLedger.findUnresolvedUnclassifiedEntries(TUK75)).willReturn(List.of(transaction));
    given(classifier.classify(any())).willReturn(new PensionFundEntryClassifier.BankAdjustment());

    var result = service.reclassify(TUK75);

    assertThat(result).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(1, 0));
    verify(fundBankLedger)
        .recordBankAdjustment(
            TUK75,
            new BigDecimal("-3.00"),
            transaction.getExternalReference(),
            SystemAccount.UNCLASSIFIED_BANK_ENTRY,
            BOOKING_DATE);
  }

  @Test
  void reclassify_doesNotBookATradeAgain_whenOneIsAlreadyBookedUnderTheSuspenseReference() {
    var transaction =
        parkedCash(new BigDecimal("-34448.28"), "DLA0000001/BDWTEIA/1000/34.44828/Buy/", "SUBS");
    given(fundBankLedger.findUnresolvedUnclassifiedEntries(TUK75)).willReturn(List.of(transaction));
    given(classifier.classify(any()))
        .willReturn(
            new PensionFundEntryClassifier.TradeSettlement(
                new TradeSettlementInfo(
                    "IE00BFG1TM61",
                    "BDWTEIA",
                    "iShares Developed World Screened Index Fund",
                    new BigDecimal("1000"))));
    given(fundBankLedger.hasLedgerEntry(transaction.getExternalReference(), TRADE_SETTLEMENT))
        .willReturn(true);

    var result = service.reclassify(TUK75);

    assertThat(result).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(1, 0));
    verify(fundBankLedger, never())
        .recordTradeSettlement(any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void reclassify_leavesAnEntryTheClassifierStillCannotPlaceInSuspenseAndBooksNothing() {
    var transaction =
        parkedCash(new BigDecimal("10.00"), "DLA0000003/NOTLISTED/5/2.00/Sell/", "TRAD");
    given(fundBankLedger.findUnresolvedUnclassifiedEntries(TUK75)).willReturn(List.of(transaction));
    given(classifier.classify(any()))
        .willReturn(new PensionFundEntryClassifier.Unclassified("unknown ticker"));

    var result = service.reclassify(TUK75);

    assertThat(result).isEqualTo(new SuspenseReclassificationService.ReclassificationResult(0, 1));
    verify(fundBankLedger).findUnresolvedUnclassifiedEntries(TUK75);
    verifyNoMoreInteractions(fundBankLedger);
  }

  private static LedgerTransaction parkedCash(
      BigDecimal amount, String remittanceInformation, String subFamilyCode) {
    var cashAccount =
        ledgerAccount(SystemAccount.FUND_INVESTMENT_CASH_CLEARING.getAccountName(TUK75));
    var suspenseAccount =
        ledgerAccount(SystemAccount.UNCLASSIFIED_BANK_ENTRY.getAccountName(TUK75));
    return suspenseTransaction(
        List.of(ledgerEntry(cashAccount, amount), ledgerEntry(suspenseAccount, amount.negate())),
        Map.of("description", remittanceInformation, "subFamilyCode", subFamilyCode));
  }

  private static LedgerAccount ledgerAccount(String name) {
    return LedgerAccount.builder()
        .name(name)
        .purpose(AccountPurpose.SYSTEM_ACCOUNT)
        .accountType(AccountType.ASSET)
        .assetType(AssetType.EUR)
        .build();
  }

  private static LedgerEntry ledgerEntry(LedgerAccount account, BigDecimal amount) {
    return LedgerEntry.builder().account(account).amount(amount).assetType(AssetType.EUR).build();
  }

  private static LedgerTransaction suspenseTransaction(
      List<LedgerEntry> entries, Map<String, Object> metadata) {
    return LedgerTransaction.builder()
        .transactionType(UNCLASSIFIED_BANK_ENTRY)
        .transactionDate(Instant.parse("2026-02-10T00:00:00Z"))
        .externalReference(UUID.randomUUID())
        .metadata(metadata)
        .entries(entries)
        .build();
  }
}
