package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY_REVERSAL;
import static ee.tuleva.onboarding.ledger.MirrorOutcome.POSTED;
import static ee.tuleva.onboarding.ledger.MirrorOutcome.REVERSED;
import static ee.tuleva.onboarding.ledger.MirrorOutcome.REVISED;
import static ee.tuleva.onboarding.ledger.MirrorOutcome.UNCHANGED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.ledger.LedgerTransactionService.LedgerEntryDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
class JournalEntryWriter {

  static final String REPLACED_SOURCE_KEYS_METADATA_KEY = "replacedSourceKeys";
  static final String SOURCE_METADATA_KEY = "source";
  static final String SOURCE_KEY_METADATA_KEY = "sourceKey";

  private static final Set<String> METADATA_A_REVERSAL_KEEPS =
      Set.of(SOURCE_KEY_METADATA_KEY, "documentType", SOURCE_METADATA_KEY);

  private final TransactionTemplate transactionTemplate;
  private final JdbcClient jdbcClient;
  private final LedgerTransactionRepository transactionRepository;
  private final LedgerTransactionService transactionService;
  private final GeneralLedgerAccounts accounts;

  static UUID reference(String entity, String source, String sourceKey) {
    return UUID.nameUUIDFromBytes(
        ("GENERAL_LEDGER:" + entity + ":" + source + ":" + sourceKey).getBytes(UTF_8));
  }

  MirrorOutcome post(String entity, String source, JournalEntryPart part) {
    UUID reference = reference(entity, source, part.sourceKey());
    String fingerprint = JournalEntryFingerprint.of(part);
    return requireNonNull(
        transactionTemplate.execute(
            status -> {
              lock(reference);
              var versions = versionsOf(reference);
              var live = liveVersion(reference, versions);
              if (live.isPresent() && isFingerprinted(live.get(), fingerprint)) {
                return UNCHANGED;
              }
              live.ifPresent(this::reverseVersion);
              postRevision(
                  entity,
                  part,
                  reference,
                  metadata(source, part, fingerprint, versions.size() + 1));
              return live.isPresent() ? REVISED : POSTED;
            }));
  }

  MirrorOutcome reverse(LiveJournalEntry seen) {
    return requireNonNull(
        transactionTemplate.execute(
            status -> {
              lock(seen.externalReference());
              if (isReversed(seen.id())) {
                return UNCHANGED;
              }
              reverseVersion(journalEntry(seen.id()));
              return REVERSED;
            }));
  }

  private void lock(UUID reference) {
    jdbcClient
        .sql("SELECT pg_advisory_xact_lock(:key)")
        .param("key", reference.getMostSignificantBits() ^ reference.getLeastSignificantBits())
        .query((rs, rowNum) -> 0)
        .optional();
  }

  private List<LedgerTransaction> versionsOf(UUID reference) {
    return transactionRepository.findAllByExternalReferenceAndTransactionType(
        reference, JOURNAL_ENTRY);
  }

  private Optional<LedgerTransaction> liveVersion(
      UUID reference, List<LedgerTransaction> versions) {
    var live = versions.stream().filter(version -> !isReversed(version.getId())).toList();
    if (live.size() > 1) {
      throw new IllegalStateException(
          "General ledger part has more than one live version: externalReference="
              + reference
              + ", liveVersions="
              + live.size());
    }
    return live.stream().findFirst();
  }

  private LedgerTransaction journalEntry(UUID id) {
    return transactionRepository
        .findById(id)
        .orElseThrow(() -> new IllegalStateException("Journal entry missing: id=" + id));
  }

  private boolean isReversed(UUID journalEntryId) {
    return transactionRepository.existsByExternalReferenceAndTransactionType(
        journalEntryId, JOURNAL_ENTRY_REVERSAL);
  }

  private void reverseVersion(LedgerTransaction version) {
    var metadata = new HashMap<>(version.getMetadata());
    metadata.keySet().retainAll(METADATA_A_REVERSAL_KEEPS);
    transactionService.createTransaction(
        JOURNAL_ENTRY_REVERSAL,
        version.getTransactionDate(),
        version.getId(),
        metadata,
        version.getEntries().stream()
            .map(entry -> new LedgerEntryDto(entry.getAccount(), entry.getAmount().negate()))
            .toArray(LedgerEntryDto[]::new));
  }

  private void postRevision(
      String entity, JournalEntryPart part, UUID reference, Map<String, Object> metadata) {
    transactionService.createTransaction(
        JOURNAL_ENTRY,
        part.transactionDate(),
        reference,
        metadata,
        part.lines().stream()
            .map(
                line ->
                    new LedgerEntryDto(accounts.resolve(entity, line.accountCode()), line.amount()))
            .toArray(LedgerEntryDto[]::new));
  }

  private static boolean isFingerprinted(LedgerTransaction version, String fingerprint) {
    return fingerprint.equals(version.getMetadata().get(JournalEntryFingerprint.METADATA_KEY));
  }

  private static Map<String, Object> metadata(
      String source, JournalEntryPart part, String fingerprint, int revision) {
    return Map.of(
        SOURCE_KEY_METADATA_KEY,
        part.sourceKey(),
        "documentType",
        part.documentType(),
        JournalEntryFingerprint.METADATA_KEY,
        fingerprint,
        SOURCE_METADATA_KEY,
        source,
        "revision",
        revision,
        REPLACED_SOURCE_KEYS_METADATA_KEY,
        part.replacedSourceKeys().stream().sorted().toList());
  }
}
