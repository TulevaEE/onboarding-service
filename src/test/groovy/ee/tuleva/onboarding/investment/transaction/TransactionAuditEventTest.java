package ee.tuleva.onboarding.investment.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TransactionAuditEventTest {

  @Test
  void onCreate_setsCreatedAtWhenNull() {
    var event = TransactionAuditEvent.builder().eventType("TEST").build();

    event.onCreate();

    assertThat(event.getCreatedAt()).isNotNull();
  }

  @Test
  void onCreate_preservesExistingCreatedAt() {
    var existingTime = Instant.parse("2026-01-10T12:00:00Z");
    var event = TransactionAuditEvent.builder().eventType("TEST").createdAt(existingTime).build();

    event.onCreate();

    assertThat(event.getCreatedAt()).isEqualTo(existingTime);
  }
}
