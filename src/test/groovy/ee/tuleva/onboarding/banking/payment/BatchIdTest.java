package ee.tuleva.onboarding.banking.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchIdTest {

  private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");

  @Test
  void theSameBatchGetsTheSameIdSoARetryReachesTheBankUnderTheSameIdempotencyKey() {
    assertThat(BatchId.of("subscription", List.of(FIRST, SECOND)))
        .isEqualTo(BatchId.of("subscription", List.of(FIRST, SECOND)));
  }

  @Test
  void memberOrderDoesNotChangeTheId() {
    assertThat(BatchId.of("subscription", List.of(FIRST, SECOND)))
        .isEqualTo(BatchId.of("subscription", List.of(SECOND, FIRST)));
  }

  @Test
  void aDifferentBatchGetsADifferentId() {
    assertThat(BatchId.of("subscription", List.of(FIRST, SECOND)))
        .isNotEqualTo(BatchId.of("subscription", List.of(FIRST)));
  }

  @Test
  void twoPurposesOverTheSameMembersDoNotCollide() {
    assertThat(BatchId.of("subscription", List.of(FIRST)))
        .isNotEqualTo(BatchId.of("redemption", List.of(FIRST)));
  }
}
