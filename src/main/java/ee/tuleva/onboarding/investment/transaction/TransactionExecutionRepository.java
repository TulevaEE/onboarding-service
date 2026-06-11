package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionExecutionRepository extends JpaRepository<TransactionExecution, Long> {

  Optional<TransactionExecution> findByOrderId(Long orderId);

  Optional<TransactionExecution> findByBrokerTransactionId(String brokerTransactionId);

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

  // Trade-date cost attribution: a trade's commission and settlement fee count in the
  // period it executes. Half-open [fromInclusive, toExclusive) on the execution timestamp
  // so last-day intraday trades are included rather than dropped at a date boundary.
  @Query(
      value =
          """
          SELECT COALESCE(SUM(
              COALESCE(e.commission_amount, 0) + COALESCE(e.settlement_fee_amount, 0)
          ), 0)
          FROM investment_transaction_execution e
          JOIN investment_transaction_order o ON e.order_id = o.id
          WHERE o.fund_code = :fundCode
            AND e.execution_timestamp >= :fromInclusive
            AND e.execution_timestamp < :toExclusive
          """,
      nativeQuery = true)
  BigDecimal sumCommissionsForFundAndPeriod(
      String fundCode, Instant fromInclusive, Instant toExclusive);
}
