package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerAccount.AssetType.EUR;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.JOURNAL_ENTRY_REVERSAL;
import static ee.tuleva.onboarding.ledger.MirrorOutcome.POSTED;
import static ee.tuleva.onboarding.ledger.MirrorOutcome.QUARANTINED;
import static ee.tuleva.onboarding.ledger.MirrorOutcome.QUARANTINED_WITH_LIVE_VERSION;
import static ee.tuleva.onboarding.ledger.MirrorOutcome.REVERSED;
import static ee.tuleva.onboarding.ledger.MirrorOutcome.REVISED;
import static ee.tuleva.onboarding.ledger.MirrorOutcome.UNCHANGED;
import static java.math.BigDecimal.ZERO;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneralLedger {

  private static final int ABSENT_PARTS_REVERSIBLE_WHATEVER_THE_SHARE = 10;

  private final GeneralLedgerAccounts accounts;
  private final JournalEntryWriter writer;
  private final LedgerTransactionRepository transactionRepository;

  @Transactional
  public void upsertAccounts(String entity, List<GeneralLedgerAccount> accounts) {
    requireColonFreeName("entity", entity);
    this.accounts.upsert(entity, accounts);
  }

  public MirrorResult mirror(
      String entity, String source, List<JournalEntryPart> parts, double maxDeletionShare) {
    requireColonFreeName("entity", entity);
    requireColonFreeName("source", source);
    requireAShare(maxDeletionShare);
    refuseToJoinACallersTransaction(entity);
    var live = liveVersions(entity, source);
    var present =
        parts.stream()
            .collect(
                toMap(
                    part -> JournalEntryWriter.reference(entity, source, part.sourceKey()),
                    identity(),
                    GeneralLedger::sameSourceKeyTwice));
    var absent =
        live.values().stream()
            .filter(entry -> !present.containsKey(entry.externalReference()))
            .toList();
    refuseMassDeletion(
        entity, parts.size(), live.size(), unexplained(absent, parts).size(), maxDeletionShare);

    var knownCodes = accounts.codesOf(entity);
    var heldRetirements = retirementsWithAFormerPartThatCannotPost(absent, parts, knownCodes);
    var heldBackParts =
        heldRetirements.stream()
            .flatMap(entry -> entry.replacedSourceKeys().stream())
            .collect(toSet());
    var partOutcomes =
        parts.stream()
            .map(
                part ->
                    heldBackParts.contains(part.sourceKey())
                        ? quarantined(
                            live.get(
                                JournalEntryWriter.reference(entity, source, part.sourceKey())))
                        : mirrorPart(entity, source, part, live, knownCodes))
            .toList();
    logQuarantined(entity, parts, partOutcomes);
    var heldByAQuarantinedReplacement =
        referencesReplacedByQuarantinedParts(entity, source, parts, partOutcomes);
    var reversals =
        absent.stream()
            .filter(entry -> !heldByAQuarantinedReplacement.contains(entry.externalReference()))
            .filter(not(heldRetirements::contains))
            .map(writer::reverse)
            .toList();
    return tally(Stream.concat(partOutcomes.stream(), reversals.stream()).toList());
  }

  private static List<LiveJournalEntry> unexplained(
      List<LiveJournalEntry> absent, List<JournalEntryPart> parts) {
    var presentSourceKeys = parts.stream().map(JournalEntryPart::sourceKey).collect(toSet());
    var replacedByPresentParts =
        parts.stream().flatMap(part -> part.replacedSourceKeys().stream()).collect(toSet());
    return absent.stream()
        .filter(entry -> !replacedByPresentParts.contains(entry.sourceKey()))
        .filter(
            entry ->
                entry.replacedSourceKeys().isEmpty()
                    || !presentSourceKeys.containsAll(entry.replacedSourceKeys()))
        .toList();
  }

  private static Set<LiveJournalEntry> retirementsWithAFormerPartThatCannotPost(
      List<LiveJournalEntry> absent, List<JournalEntryPart> parts, Set<String> knownCodes) {
    var unpostable =
        parts.stream()
            .filter(part -> !isPostable(part, knownCodes))
            .map(JournalEntryPart::sourceKey)
            .collect(toSet());
    return absent.stream()
        .filter(entry -> entry.replacedSourceKeys().stream().anyMatch(unpostable::contains))
        .collect(toSet());
  }

  public Set<String> liveSourceKeys(String entity, String source) {
    return liveVersions(entity, source).values().stream()
        .map(LiveJournalEntry::sourceKey)
        .collect(toSet());
  }

  private Map<UUID, LiveJournalEntry> liveVersions(String entity, String source) {
    return transactionRepository
        .findLiveJournalEntries(
            GeneralLedgerAccounts.accountNamePattern(entity), JOURNAL_ENTRY, JOURNAL_ENTRY_REVERSAL)
        .stream()
        .filter(entry -> entry.isFrom(source))
        .collect(toMap(LiveJournalEntry::externalReference, identity(), GeneralLedger::twoLive));
  }

  private MirrorOutcome mirrorPart(
      String entity,
      String source,
      JournalEntryPart part,
      Map<UUID, LiveJournalEntry> live,
      Set<String> knownCodes) {
    var current = live.get(JournalEntryWriter.reference(entity, source, part.sourceKey()));
    if (!isPostable(part, knownCodes)) {
      return quarantined(current);
    }
    if (current != null && current.fingerprint().equals(JournalEntryFingerprint.of(part))) {
      return UNCHANGED;
    }
    try {
      return writer.post(entity, source, part);
    } catch (ConstraintViolationException e) {
      return quarantined(current);
    }
  }

  private static MirrorOutcome quarantined(@Nullable LiveJournalEntry current) {
    return current == null ? QUARANTINED : QUARANTINED_WITH_LIVE_VERSION;
  }

  private static boolean isPostable(JournalEntryPart part, Set<String> knownCodes) {
    return part.lines().size() >= 2
        && part.lines().stream()
                .map(JournalEntryLine::amount)
                .reduce(ZERO, BigDecimal::add)
                .signum()
            == 0
        && part.lines().stream().map(JournalEntryLine::accountCode).allMatch(knownCodes::contains)
        && part.lines().stream()
            .map(JournalEntryLine::amount)
            .allMatch(GeneralLedger::fitsTheLedger);
  }

  private static boolean fitsTheLedger(BigDecimal amount) {
    final int LEDGER_ENTRY_INTEGER_DIGITS = 15;
    var stripped = amount.stripTrailingZeros();
    return stripped.scale() <= EUR.getMaxPrecision()
        && stripped.precision() - stripped.scale() <= LEDGER_ENTRY_INTEGER_DIGITS;
  }

  private static void requireColonFreeName(String field, String name) {
    if (name.isBlank() || name.contains(":")) {
      throw new IllegalArgumentException(
          "General ledger name must be non-blank and without a colon: " + field + "=" + name);
    }
  }

  private static void requireAShare(double maxDeletionShare) {
    if (!(maxDeletionShare >= 0 && maxDeletionShare <= 1)) {
      throw new IllegalArgumentException(
          "General ledger deletion share must be between 0 and 1: maxDeletionShare="
              + maxDeletionShare);
    }
  }

  private static void refuseToJoinACallersTransaction(String entity) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "General ledger mirror commits every part on its own and cannot join a transaction:"
              + " entity="
              + entity);
    }
  }

  private static void refuseMassDeletion(
      String entity, int partCount, int liveCount, int absentCount, double maxDeletionShare) {
    if (partCount == 0 && liveCount > 0) {
      throw new IllegalStateException(
          "General ledger source has no parts while live parts exist: entity="
              + entity
              + ", liveParts="
              + liveCount);
    }
    if (absentCount
        > Math.max(ABSENT_PARTS_REVERSIBLE_WHATEVER_THE_SHARE, maxDeletionShare * liveCount)) {
      throw new IllegalStateException(
          "General ledger source is missing too many live parts: entity="
              + entity
              + ", absentParts="
              + absentCount
              + ", liveParts="
              + liveCount
              + ", maxDeletionShare="
              + maxDeletionShare);
    }
  }

  private static void logQuarantined(
      String entity, List<JournalEntryPart> parts, List<MirrorOutcome> outcomes) {
    var sourceKeys =
        IntStream.range(0, parts.size())
            .filter(index -> outcomes.get(index).isQuarantined())
            .mapToObj(index -> parts.get(index).sourceKey())
            .toList();
    if (!sourceKeys.isEmpty()) {
      log.error(
          "General ledger parts quarantined: entity={}, count={}, withLiveVersion={},"
              + " firstSourceKeys={}",
          entity,
          sourceKeys.size(),
          Collections.frequency(outcomes, QUARANTINED_WITH_LIVE_VERSION),
          sourceKeys.stream().limit(10).toList());
    }
  }

  private static Set<UUID> referencesReplacedByQuarantinedParts(
      String entity, String source, List<JournalEntryPart> parts, List<MirrorOutcome> outcomes) {
    return IntStream.range(0, parts.size())
        .filter(index -> outcomes.get(index).isQuarantined())
        .mapToObj(index -> parts.get(index).replacedSourceKeys())
        .flatMap(Set::stream)
        .map(sourceKey -> JournalEntryWriter.reference(entity, source, sourceKey))
        .collect(toSet());
  }

  private static MirrorResult tally(List<MirrorOutcome> outcomes) {
    return new MirrorResult(
        Collections.frequency(outcomes, POSTED),
        Collections.frequency(outcomes, REVISED),
        Collections.frequency(outcomes, REVERSED),
        Collections.frequency(outcomes, UNCHANGED),
        (int) outcomes.stream().filter(MirrorOutcome::isQuarantined).count(),
        Collections.frequency(outcomes, QUARANTINED_WITH_LIVE_VERSION));
  }

  private static JournalEntryPart sameSourceKeyTwice(JournalEntryPart one, JournalEntryPart other) {
    throw new IllegalStateException(
        "General ledger source has a source key twice: sourceKey=" + one.sourceKey());
  }

  private static LiveJournalEntry twoLive(LiveJournalEntry one, LiveJournalEntry other) {
    throw new IllegalStateException(
        "General ledger part has more than one live version: externalReference="
            + one.externalReference()
            + ", ids="
            + List.of(one.id(), other.id()));
  }
}
