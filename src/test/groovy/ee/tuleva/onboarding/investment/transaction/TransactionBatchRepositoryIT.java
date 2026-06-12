package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.AWAITING_CONFIRMATION;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class TransactionBatchRepositoryIT {

  @Autowired private TransactionBatchRepository batchRepository;

  @Test
  void findFirstByFundOrderByCreatedAtDesc_returnsLatestBatchOfTheGivenFund() {
    persistBatch(TUK75, Instant.parse("2026-06-09T09:00:00Z"));
    TransactionBatch latestTuk75 = persistBatch(TUK75, Instant.parse("2026-06-11T09:00:00Z"));
    persistBatch(TUK75, Instant.parse("2026-06-10T09:00:00Z"));
    persistBatch(TUK00, Instant.parse("2026-06-12T09:00:00Z"));

    assertThat(batchRepository.findFirstByFundOrderByCreatedAtDesc(TUK75))
        .map(TransactionBatch::getId)
        .contains(latestTuk75.getId());
  }

  @Test
  void findFirstByFundOrderByCreatedAtDesc_returnsEmptyWhenFundHasNoBatches() {
    persistBatch(TUK75, Instant.parse("2026-06-11T09:00:00Z"));

    assertThat(batchRepository.findFirstByFundOrderByCreatedAtDesc(TUK00)).isEmpty();
  }

  private TransactionBatch persistBatch(TulevaFund fund, Instant createdAt) {
    return batchRepository.save(
        TransactionBatch.builder()
            .fund(fund)
            .status(AWAITING_CONFIRMATION)
            .createdBy("system")
            .createdAt(createdAt)
            .metadata(Map.of())
            .build());
  }
}
