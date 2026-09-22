package ee.tuleva.onboarding.banking.payment;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Collection;
import java.util.UUID;

public final class BatchId {
  private BatchId() {}

  public static UUID of(String purpose, Collection<UUID> memberIds) {
    var canonical =
        purpose + ":" + memberIds.stream().map(UUID::toString).sorted().reduce("", String::concat);
    return UUID.nameUUIDFromBytes(canonical.getBytes(UTF_8));
  }
}
