package ee.tuleva.onboarding.investment.transaction;

import static org.springframework.http.HttpStatus.CONFLICT;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
class BatchConfirmations {

  static final String ORDER_TYPE_CHANGED = "ORDER_TYPE_CHANGED";
  private static final String BATCH_FIRST_CONFIRMED = "BATCH_FIRST_CONFIRMED";

  private final TransactionBatchRepository batchRepository;
  private final TransactionAuditEventRepository auditEventRepository;
  private final TwoConfirmationsRule rule;
  private final Clock clock;

  void giveFirst(TransactionBatch batch, Long reviewedVersion, String actor) {
    rule.requireAllowedToGiveFirstConfirmation(batch, reviewedVersion, actor);
    lock(batch, batchRepository::lockAgainstConcurrentChange);
    log.info(
        "Transaction batch first confirmation: id={}, version={}, actor={}",
        batch.getId(),
        batch.getVersion(),
        actor);
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .batch(batch)
            .eventType(BATCH_FIRST_CONFIRMED)
            .actor(actor)
            .createdAt(Instant.now(clock))
            .payload(
                TwoConfirmationsRule.firstConfirmationPayload(
                    batch, actor, orderTypeChangesIn(batch)))
            .build());
  }

  OrderSender requireAllowedToSend(TransactionBatch batch, Long reviewedVersion, String sender) {
    return rule.requireAllowedToSend(
        batch,
        reviewedVersion,
        sender,
        auditEventRepository.findByBatchIdAndEventType(batch.getId(), BATCH_FIRST_CONFIRMED),
        orderTypeChangesIn(batch));
  }

  void lockForOrderTypeChange(TransactionBatch batch) {
    lock(batch, batchRepository::lockAgainstConcurrentConfirmation);
  }

  private long orderTypeChangesIn(TransactionBatch batch) {
    return auditEventRepository.countOrderEventsInBatch(batch.getId(), ORDER_TYPE_CHANGED);
  }

  private static void lock(TransactionBatch batch, Consumer<TransactionBatch> lock) {
    try {
      lock.accept(batch);
    } catch (ConcurrencyFailureException e) {
      throw new ResponseStatusException(
          CONFLICT, "Batch was modified concurrently: id=" + batch.getId());
    }
  }
}
