package ee.tuleva.onboarding.banking.seb.processor;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.BANK_FEE;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.UNCLASSIFIED_BANK_ENTRY;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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
  void reclassify_throwsWhenTheTransactionHasNoCashLeg() {
    var suspenseAccount =
        ledgerAccount(SystemAccount.UNCLASSIFIED_BANK_ENTRY.getAccountName(TUK75));
    var onlyEntry = ledgerEntry(suspenseAccount, new BigDecimal("50.00"));
    var otherEntry = ledgerEntry(suspenseAccount, new BigDecimal("-50.00"));

    var transaction =
        suspenseTransaction(List.of(onlyEntry, otherEntry), Map.of("description", "test entry"));

    given(fundBankLedger.findUnresolvedUnclassifiedEntries(TUK75)).willReturn(List.of(transaction));

    assertThatThrownBy(() -> service.reclassify(TUK75)).isInstanceOf(IllegalStateException.class);
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
