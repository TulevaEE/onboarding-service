package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.INCOME;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY_REVERSAL;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType;
import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({LedgerAccountService.class, LedgerTransactionService.class, UserUnitBalanceGuard.class})
class LedgerTransactionRepositoryTest {

  private static final String TULEVA_FONDID_ACCOUNTS = "GENERAL\\_LEDGER:TULEVA\\_FONDID:%";
  private static final Instant DATE = Instant.parse("2026-01-31T21:59:59.999999Z");

  @Autowired LedgerTransactionRepository transactionRepository;
  @Autowired LedgerAccountService accountService;
  @Autowired LedgerTransactionService transactionService;

  @Test
  void liveJournalEntriesOfAnEntityLeaveOutReversedVersionsAndOtherEntities() {
    var reference = UUID.randomUUID();
    var reversed = journalEntry("TULEVA_FONDID", reference, "first");
    reverse(reversed, "TULEVA_FONDID");
    var live = journalEntry("TULEVA_FONDID", reference, "second");
    journalEntry("TULEVAXFONDID", UUID.randomUUID(), "look-alike entity");
    journalEntry("OTHER_ENTITY", UUID.randomUUID(), "other entity");

    var liveEntries =
        transactionRepository.findLiveJournalEntries(
            TULEVA_FONDID_ACCOUNTS, JOURNAL_ENTRY, JOURNAL_ENTRY_REVERSAL);

    assertThat(liveEntries)
        .containsExactly(
            new LiveJournalEntry(live.getId(), reference, Map.of("fingerprint", "second")));
    assertThat(liveEntries.getFirst().fingerprint()).isEqualTo("second");
  }

  @Test
  void journalEntriesByReferenceIncludeEveryRevision() {
    var reference = UUID.randomUUID();
    var first = journalEntry("TULEVA_FONDID", reference, "first");
    reverse(first, "TULEVA_FONDID");
    var second = journalEntry("TULEVA_FONDID", reference, "second");
    journalEntry("TULEVA_FONDID", UUID.randomUUID(), "another part");

    assertThat(
            transactionRepository.findAllByExternalReferenceAndTransactionType(
                reference, JOURNAL_ENTRY))
        .extracting(LedgerTransaction::getId)
        .containsExactlyInAnyOrder(first.getId(), second.getId());
  }

  @Test
  void aReversalIsFoundByTheIdOfTheVersionItReverses() {
    var reference = UUID.randomUUID();
    var reversed = journalEntry("TULEVA_FONDID", reference, "first");
    reverse(reversed, "TULEVA_FONDID");
    var live = journalEntry("TULEVA_FONDID", reference, "second");

    assertThat(
            transactionRepository.existsByExternalReferenceAndTransactionType(
                reversed.getId(), JOURNAL_ENTRY_REVERSAL))
        .isTrue();
    assertThat(
            transactionRepository.existsByExternalReferenceAndTransactionType(
                live.getId(), JOURNAL_ENTRY_REVERSAL))
        .isFalse();
  }

  private LedgerTransaction journalEntry(String entity, UUID reference, String fingerprint) {
    return post(entity, JOURNAL_ENTRY, reference, Map.of("fingerprint", fingerprint), "10.00");
  }

  private void reverse(LedgerTransaction version, String entity) {
    post(entity, JOURNAL_ENTRY_REVERSAL, version.getId(), Map.of(), "-10.00");
  }

  private LedgerTransaction post(
      String entity,
      TransactionType type,
      @Nullable UUID reference,
      Map<String, Object> metadata,
      String bankAmount) {
    var bank = account(entity, "100100", ASSET);
    var income = account(entity, "400100", INCOME);
    var amount = new BigDecimal(bankAmount);
    return transactionService.createTransaction(
        type,
        DATE,
        reference,
        metadata,
        new LedgerEntryDto(bank, amount),
        new LedgerEntryDto(income, amount.negate()));
  }

  private LedgerAccount account(String entity, String code, LedgerAccount.AccountType accountType) {
    return accountService.createSystemAccount(
        "GENERAL_LEDGER:" + entity + ":" + code, accountType, EUR);
  }
}
