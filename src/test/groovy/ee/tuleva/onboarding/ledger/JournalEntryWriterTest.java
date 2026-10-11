package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.ASSET;
import static ee.tuleva.onboarding.ledger.LedgerAccount.AccountType.INCOME;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY_REVERSAL;
import static ee.tuleva.onboarding.ledger.MirrorOutcome.UNCHANGED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import ee.tuleva.onboarding.ledger.GeneralLedgerRows.JournalEntryVersion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(GeneralLedgerStackConfiguration.class)
@Transactional(propagation = NOT_SUPPORTED)
class JournalEntryWriterTest {

  private static final String ENTITY = "TULEVA_FONDID";
  private static final String SOURCE = "TEST_SYSTEM";
  private static final JournalEntryPart INVOICE = invoice("250.00");

  @Autowired GeneralLedger generalLedger;
  @Autowired JournalEntryWriter writer;
  @Autowired LedgerTransactionRepository transactionRepository;
  @Autowired GeneralLedgerRows rows;

  @BeforeEach
  void setUp() {
    generalLedger.upsertAccounts(
        ENTITY,
        List.of(
            new GeneralLedgerAccount("100100", ASSET, Map.of("name", "Bank")),
            new GeneralLedgerAccount("400100", INCOME, Map.of("name", "Fee income"))));
  }

  @AfterEach
  void tearDown() {
    rows.deleteAll();
  }

  @Test
  void aPartsReferenceIsTheNameBasedUuidOfEntitySourceAndSourceKey() {
    assertThat(JournalEntryWriter.reference(ENTITY, SOURCE, "ARVE:1:2026-01-31"))
        .isEqualTo(
            UUID.nameUUIDFromBytes(
                "GENERAL_LEDGER:TULEVA_FONDID:TEST_SYSTEM:ARVE:1:2026-01-31".getBytes(UTF_8)));
  }

  @Test
  void postingAPartAnotherRunAlreadyPostedWithTheSameContentIsUnchanged() {
    writer.post(ENTITY, SOURCE, INVOICE);

    var outcome = writer.post(ENTITY, SOURCE, INVOICE);

    assertThat(outcome).isEqualTo(UNCHANGED);
    assertThat(rows.count(JOURNAL_ENTRY)).isEqualTo(1);
    assertThat(rows.count(JOURNAL_ENTRY_REVERSAL)).isZero();
  }

  @Test
  void aReversalLeavesARevisionPostedAfterTheRunReadItsSnapshot() {
    writer.post(ENTITY, SOURCE, INVOICE);
    var seenByAStaleRun = liveEntryOf(INVOICE);
    writer.post(ENTITY, SOURCE, invoice("275.00"));

    var outcome = writer.reverse(seenByAStaleRun);

    assertThat(outcome).isEqualTo(UNCHANGED);
    assertThat(rows.versionsOf(ENTITY, SOURCE, INVOICE.sourceKey()))
        .extracting(JournalEntryVersion::live)
        .containsExactly(false, true);
    assertThat(rows.count(JOURNAL_ENTRY_REVERSAL)).isEqualTo(1);
  }

  @Test
  void aVersionAnotherRunAlreadyReversedIsNotReversedTwice() {
    writer.post(ENTITY, SOURCE, INVOICE);
    var seenByBothRuns = liveEntryOf(INVOICE);
    writer.reverse(seenByBothRuns);

    var outcome = writer.reverse(seenByBothRuns);

    assertThat(outcome).isEqualTo(UNCHANGED);
    assertThat(rows.count(JOURNAL_ENTRY_REVERSAL)).isEqualTo(1);
  }

  private LiveJournalEntry liveEntryOf(JournalEntryPart part) {
    var reference = JournalEntryWriter.reference(ENTITY, SOURCE, part.sourceKey());
    return transactionRepository
        .findLiveJournalEntries(
            "GENERAL\\_LEDGER:TULEVA\\_FONDID:%", JOURNAL_ENTRY, JOURNAL_ENTRY_REVERSAL)
        .stream()
        .filter(entry -> entry.externalReference().equals(reference))
        .findFirst()
        .orElseThrow();
  }

  private static JournalEntryPart invoice(String amount) {
    return new JournalEntryPart(
        "ARVE:1:2026-01-31",
        "ARVE",
        LocalDate.parse("2026-01-31"),
        List.of(
            new JournalEntryLine("100100", new BigDecimal(amount)),
            new JournalEntryLine("400100", new BigDecimal(amount).negate())),
        Set.of());
  }
}
