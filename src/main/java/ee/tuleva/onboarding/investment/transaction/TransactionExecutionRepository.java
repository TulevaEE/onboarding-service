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
  // window, per side. A trade reaches the custodian position report once SEB reports it in the
  // pending transactions file — for an ETF the day after we send, for a fund a few days later,
  // for the CCF only on settlement day — so the window is anchored on the report that first
  // carried it. reported_date is the report's "As of" date, the same clock fund_position.nav_date
  // runs on, so both sides of the comparison move on the same event. Anchoring on settlement, or
  // on our own ingestion instant, would count a trade against a later position report than the
  // one whose quantity it moved, and every trade would look unexplained twice: once where the
  // quantity moved, once where the window put it.
  //
  // Historical-import rows are excluded because the question does not apply to them: they were
  // loaded from a registry export, never from a custodian report, so there is no report whose "As
  // of" date could date them. reported_date carries a best guess for those rows and the guess ends
  // at the import instant, which would place a pre-system trade in whatever window happens to be
  // open. The trades they describe predate the position baseline, so nothing recent is theirs to
  // explain.
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
