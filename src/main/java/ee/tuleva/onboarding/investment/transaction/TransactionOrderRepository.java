package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.fund.TulevaFund;
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

  default List<TransactionOrder> findOverdueOrders(OrderStatus... statuses) {
    return findByOrderStatusIn(Arrays.asList(statuses));
  }
}
