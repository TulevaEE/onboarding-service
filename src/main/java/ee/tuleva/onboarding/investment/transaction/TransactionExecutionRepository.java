package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionExecutionRepository extends JpaRepository<TransactionExecution, Long> {

  List<TransactionExecution> findAllByOrderId(Long orderId);

  Optional<TransactionExecution> findByBrokerTransactionId(String brokerTransactionId);

  List<TransactionExecution> findAllByBrokerTransactionId(String brokerTransactionId);

  List<TransactionExecution> findByOrderIdIn(Collection<Long> orderIds);

  @Query(
      """
      SELECT e FROM TransactionExecution e
      WHERE e.orderId IN (:orderIds)
        AND e.executionTimestamp >= :fromInclusive
        AND e.executionTimestamp < :toExclusive
      """)
  List<TransactionExecution> findByOrderIdInAndExecutionTimestampInRange(
      Collection<Long> orderIds, Instant fromInclusive, Instant toExclusive);

  @Query(
      value =
          """
          SELECT o.instrument_isin AS isin,
                 SUM(CASE WHEN o.transaction_type = 'BUY' THEN e.executed_quantity ELSE 0 END)
                   AS bought,
                 SUM(CASE WHEN o.transaction_type = 'SELL' THEN e.executed_quantity ELSE 0 END)
                   AS sold
          FROM investment_transaction_execution e
          JOIN investment_transaction_order o ON o.id = e.order_id
          WHERE o.fund_code = :fundCode
            AND o.order_status NOT IN ('CANCELLED', 'DISCARDED')
            AND e.executed_quantity IS NOT NULL
            AND e.source <> 'HISTORICAL_IMPORT'
            AND e.reported_date > :fromExclusive
            AND e.reported_date <= :toInclusive
          GROUP BY o.instrument_isin
          """,
      nativeQuery = true)
  List<ExecutedQuantitySummary> sumExecutedQuantitiesByIsin(
      String fundCode, LocalDate fromExclusive, LocalDate toInclusive);

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
