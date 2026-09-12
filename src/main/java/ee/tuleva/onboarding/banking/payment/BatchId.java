package ee.tuleva.onboarding.banking.payment;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Collection;
import java.util.UUID;

/**
 * A batch identifier derived from the batch's own contents.
 *
 * <p>The transfers used {@code UUID.randomUUID()}, which made the bank's Idempotency-Key different
 * on every attempt. Since the payment file is submitted from inside the caller's transaction, a
 * commit failure after the bank accepted the file meant the next run rebuilt the same batch under a
 * new key and the bank had no way to recognise it as the same money. Deriving the id from the ids
 * of the payments in the batch makes a repeat of the same batch carry the same key.
 */
public final class BatchId {

  private BatchId() {}

  public static UUID of(String purpose, Collection<UUID> memberIds) {
    var canonical =
        purpose + ":" + memberIds.stream().map(UUID::toString).sorted().reduce("", String::concat);
    return UUID.nameUUIDFromBytes(canonical.getBytes(UTF_8));
  }
}
