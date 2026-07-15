package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.BatchStatus.AWAITING_CONFIRMATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.fund.TulevaFund;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@DataJpaTest
class TransactionBatchRepositoryIT {

  @Autowired private TransactionBatchRepository batchRepository;

  @Autowired private EntityManager entityManager;

  @Autowired private JdbcTemplate jdbcTemplate;

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

  @Test
  void confirmationAndCancellationColumnsRoundTrip() {
    TransactionBatch batch =
        batchRepository.save(
            TransactionBatch.builder()
                .fund(TUK75)
                .status(BatchStatus.CANCELLED)
                .createdBy("system")
                .createdAt(Instant.parse("2026-06-11T09:00:00Z"))
                .confirmedBy("operator-7")
                .confirmedAt(Instant.parse("2026-06-11T09:05:00Z"))
                .cancellationReason("duplicate batch")
                .cancelledBy("operator-9")
                .cancelledAt(Instant.parse("2026-06-11T09:10:00Z"))
                .metadata(Map.of())
                .build());

    assertThat(batchRepository.findById(batch.getId()))
        .get()
        .satisfies(
            persisted -> {
              assertThat(persisted.getStatus()).isEqualTo(BatchStatus.CANCELLED);
              assertThat(persisted.getConfirmedBy()).isEqualTo("operator-7");
              assertThat(persisted.getConfirmedAt())
                  .isEqualTo(Instant.parse("2026-06-11T09:05:00Z"));
              assertThat(persisted.getCancellationReason()).isEqualTo("duplicate batch");
              assertThat(persisted.getCancelledBy()).isEqualTo("operator-9");
              assertThat(persisted.getCancelledAt())
                  .isEqualTo(Instant.parse("2026-06-11T09:10:00Z"));
            });
  }

  @Test
  void saveAndFlush_withStaleVersion_throwsOptimisticLockingFailure() {
    TransactionBatch batch = persistBatch(TUK75, Instant.parse("2026-06-11T09:00:00Z"));
    entityManager.clear();
    TransactionBatch staleCopy = batchRepository.findById(batch.getId()).orElseThrow();

    jdbcTemplate.update(
        "UPDATE investment_transaction_batch SET version = version + 1, confirmed_by = ? WHERE id = ?",
        "operator-first",
        batch.getId());

    staleCopy.setStatus(BatchStatus.CANCELLED);
    staleCopy.setCancelledBy("operator-stale");
    assertThatThrownBy(() -> batchRepository.saveAndFlush(staleCopy))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
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
