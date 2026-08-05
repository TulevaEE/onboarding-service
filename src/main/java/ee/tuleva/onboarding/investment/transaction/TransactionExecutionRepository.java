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

  // Report sanity check: how much of an instrument we ourselves traded into a position over a
  // window, per side. A trade moves the custodian position when it SETTLES, so the window is
  // anchored on the settlement date, falling back to the trade date when the custodian gave us
  // none. Buy and sell totals stay separate because a position report shows quantities before
  // unsettled trades settle — a same-window buy and sell must not net each other out.
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
            AND COALESCE(e.scheduled_settlement_date, CAST(e.execution_timestamp AS DATE))
                  > :fromExclusive
            AND COALESCE(e.scheduled_settlement_date, CAST(e.execution_timestamp AS DATE))
                  <= :toInclusive
          GROUP BY o.instrument_isin
          """,
      nativeQuery = true)
  List<ExecutedQuantitySummary> sumExecutedQuantitiesByIsin(
      String fundCode, LocalDate fromExclusive, LocalDate toInclusive);

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
