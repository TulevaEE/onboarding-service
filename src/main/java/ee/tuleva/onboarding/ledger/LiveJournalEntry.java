package ee.tuleva.onboarding.ledger;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.UUID;

record LiveJournalEntry(UUID id, UUID externalReference, Map<String, Object> metadata) {

  boolean isFrom(String source) {
    return source.equals(metadata.get(JournalEntryWriter.SOURCE_METADATA_KEY));
  }

  String fingerprint() {
    return (String)
        requireNonNull(
            metadata.get(JournalEntryFingerprint.METADATA_KEY),
            "Journal entry without fingerprint: id=" + id);
  }
}
