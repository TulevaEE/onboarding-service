package ee.tuleva.onboarding.ledger;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

record LiveJournalEntry(UUID id, UUID externalReference, Map<String, Object> metadata) {

  boolean isFrom(String source) {
    return source.equals(metadata.get(JournalEntryWriter.SOURCE_METADATA_KEY));
  }

  String sourceKey() {
    return (String)
        requireNonNull(
            metadata.get(JournalEntryWriter.SOURCE_KEY_METADATA_KEY),
            "Journal entry without source key: id=" + id);
  }

  Set<String> replacedSourceKeys() {
    return metadata.get(JournalEntryWriter.REPLACED_SOURCE_KEYS_METADATA_KEY)
            instanceof List<?> keys
        ? keys.stream().map(String::valueOf).collect(toUnmodifiableSet())
        : Set.of();
  }

  String fingerprint() {
    return (String)
        requireNonNull(
            metadata.get(JournalEntryFingerprint.METADATA_KEY),
            "Journal entry without fingerprint: id=" + id);
  }
}
