package ee.tuleva.onboarding.investment.transaction;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionExecutionRepository extends JpaRepository<TransactionExecution, Long> {

  Optional<TransactionExecution> findByOrderId(Long orderId);

  Optional<TransactionExecution> findByBrokerTransactionId(String brokerTransactionId);

  List<TransactionExecution> findByOrderIdIn(Collection<Long> orderIds);

  // Half-open range [fromInclusive, toExclusive) so a trade-date window
  // converted to instants does not double-count midnight rows.
  @Query(
      """
      SELECT e FROM TransactionExecution e
      WHERE e.orderId IN (:orderIds)
        AND e.executionTimestamp >= :fromInclusive
        AND e.executionTimestamp < :toExclusive
      """)
  List<TransactionExecution> findByOrderIdInAndExecutionTimestampInRange(
      Collection<Long> orderIds, Instant fromInclusive, Instant toExclusive);
}
