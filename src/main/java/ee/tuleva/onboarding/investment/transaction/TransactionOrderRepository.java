package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionOrderRepository extends JpaRepository<TransactionOrder, Long> {

  List<TransactionOrder> findByBatchId(Long batchId);

  @Query(
      """
      SELECT o FROM TransactionOrder o
      WHERE o.fund = :fund
        AND o.orderStatus = ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT
        AND o.expectedSettlementDate > :asOfDate
      """)
  List<TransactionOrder> findUnsettledOrders(TulevaFund fund, LocalDate asOfDate);
}
