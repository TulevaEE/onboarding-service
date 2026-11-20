package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionOrderRepository extends JpaRepository<TransactionOrder, Long> {

  List<TransactionOrder> findByBatchId(Long batchId);

  Optional<TransactionOrder> findByOrderUuid(UUID orderUuid);

  List<TransactionOrder> findByOrderVenueAndOrderTimestampGreaterThanEqual(
      OrderVenue orderVenue, Instant since);

  List<TransactionOrder> findByInstrumentIsin(String instrumentIsin);

  List<TransactionOrder> findByFund(TulevaFund fund);

  // EXECUTED orders keep their cash impact until the expected settlement date has passed —
  // reconciliation flips SENT -> EXECUTED before cash actually settles (no settlement data yet).
  @Query(
      """
      SELECT o FROM TransactionOrder o
      WHERE o.fund = :fund
        AND (o.orderStatus = ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT
             AND o.expectedSettlementDate > :asOfDate
          OR o.orderStatus = ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED
             AND o.expectedSettlementDate >= :asOfDate)
      """)
  List<TransactionOrder> findUnsettledOrders(TulevaFund fund, LocalDate asOfDate);

  @Query(
      """
      SELECT o FROM TransactionOrder o
      WHERE o.fund = :fund
        AND (o.orderStatus = ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT
             AND o.expectedSettlementDate > :asOfDate
          OR o.orderStatus = ee.tuleva.onboarding.investment.transaction.OrderStatus.EXECUTED
             AND o.expectedSettlementDate >= :asOfDate)
        AND o.createdAt < :createdBefore
      """)
  List<TransactionOrder> findUnsettledOrdersAsOf(
      TulevaFund fund, LocalDate asOfDate, Instant createdBefore);

  @Query(
      """
      SELECT o FROM TransactionOrder o
      WHERE o.orderStatus IN (:statuses)
      """)
  List<TransactionOrder> findByOrderStatusIn(Collection<OrderStatus> statuses);

  // Bounded by orderTimestamp so the settlement check does not scan the whole
  // historical EXECUTED backlog (orders never transition to SETTLED today).
  @Query(
      """
      SELECT o FROM TransactionOrder o
      WHERE o.orderStatus IN (:statuses)
        AND o.orderTimestamp >= :since
      """)
  List<TransactionOrder> findByOrderStatusInAndOrderTimestampSince(
      Collection<OrderStatus> statuses, Instant since);

  @Query(
      """
      SELECT MIN(COALESCE(e.executionTimestamp, o.orderTimestamp))
      FROM TransactionOrder o
        JOIN TransactionCommand c ON c.batchId = o.batch.id
        LEFT JOIN TransactionExecution e ON e.orderId = o.id
      WHERE c.mode = :mode
        AND o.fund = :fund
        AND o.orderStatus IN (:statuses)
        AND o.orderTimestamp >= :from
        AND COALESCE(e.executionTimestamp, o.orderTimestamp) < :until
      """)
  Optional<Instant> findFirstTradeTimestampOfATradePrepRun(
      TulevaFund fund,
      TransactionMode mode,
      Collection<OrderStatus> statuses,
      Instant from,
      Instant until);

  @Query(
      """
      SELECT o
      FROM TransactionOrder o
        JOIN FETCH o.batch b
        JOIN TransactionCommand c ON c.batchId = b.id
      WHERE c.mode = :mode
        AND o.fund = :fund
        AND o.orderStatus = :status
        AND o.orderTimestamp >= :from
        AND o.orderTimestamp < :until
      """)
  List<TransactionOrder> findOrdersOfATradePrepRun(
      TulevaFund fund, TransactionMode mode, OrderStatus status, Instant from, Instant until);

  default List<TransactionOrder> findOverdueOrders(OrderStatus... statuses) {
    return findByOrderStatusIn(Arrays.asList(statuses));
  }
}
