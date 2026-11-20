package ee.tuleva.onboarding.investment.transaction;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionAuditEventRepository
    extends JpaRepository<TransactionAuditEvent, Long> {

  List<TransactionAuditEvent> findByBatchIdOrderByCreatedAt(Long batchId);

  List<TransactionAuditEvent> findByBatchIdAndEventType(Long batchId, String eventType);

  List<TransactionAuditEvent> findByEventType(String eventType);

  long countByEventType(String eventType);

  List<TransactionAuditEvent> findByEventTypeAndDedupKey(String eventType, String dedupKey);

  List<TransactionAuditEvent> findByOrderIdAndEventType(Long orderId, String eventType);

  @Query(
      """
      SELECT COUNT(e) FROM TransactionAuditEvent e
      WHERE e.eventType = :eventType
        AND e.orderId IN (SELECT o.id FROM TransactionOrder o WHERE o.batch.id = :batchId)
      """)
  long countOrderEventsInBatch(Long batchId, String eventType);
}
