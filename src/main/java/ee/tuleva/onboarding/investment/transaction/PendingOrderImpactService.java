package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@NullMarked
class PendingOrderImpactService {

  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final PositionPriceResolver positionPriceResolver;

  PendingOrderImpact calculate(TulevaFund fund, LocalDate asOfDate) {
    List<TransactionOrder> unsettled = orderRepository.findUnsettledOrders(fund, asOfDate);
    if (unsettled.isEmpty()) {
      return PendingOrderImpact.none();
    }

    Map<Long, BigDecimal> executedConsideration = executedConsiderationByOrderId(unsettled);

    BigDecimal pendingBuys = ZERO;
    BigDecimal pendingSells = ZERO;
    Map<String, BigDecimal> unreportedValues = new HashMap<>();
    Map<String, BigDecimal> unreportedQuantities = new HashMap<>();

    for (TransactionOrder order : unsettled) {
      BigDecimal cashImpact = cashImpact(order, executedConsideration.get(order.getId()), asOfDate);
      if (order.getTransactionType() == BUY) {
        pendingBuys = pendingBuys.add(cashImpact);
      } else {
        pendingSells = pendingSells.add(cashImpact);
      }

      if (order.getOrderStatus() != SENT) {
        continue;
      }
      BigDecimal signedValue = signed(order, estimatedValue(order, asOfDate));
      unreportedValues.merge(order.getInstrumentIsin(), signedValue, BigDecimal::add);
      if (order.getInstrumentType() == ETF && order.getOrderQuantity() != null) {
        unreportedQuantities.merge(
            order.getInstrumentIsin(), signed(order, order.getOrderQuantity()), BigDecimal::add);
      }
    }

    log.info(
        "Pending order impact: fund={}, asOfDate={}, orderCount={}, pendingBuys={},"
            + " pendingSells={}, unreportedIsins={}",
        fund,
        asOfDate,
        unsettled.size(),
        pendingBuys.toPlainString(),
        pendingSells.toPlainString(),
        unreportedValues.keySet());

    return new PendingOrderImpact(
        pendingBuys, pendingSells, Map.copyOf(unreportedValues), Map.copyOf(unreportedQuantities));
  }

  private Map<Long, BigDecimal> executedConsiderationByOrderId(List<TransactionOrder> orders) {
    List<Long> orderIds =
        orders.stream().map(TransactionOrder::getId).filter(Objects::nonNull).toList();
    return executionRepository.findByOrderIdIn(orderIds).stream()
        .filter(execution -> execution.getTotalConsideration() != null)
        .collect(
            Collectors.groupingBy(
                TransactionExecution::getOrderId,
                Collectors.reducing(
                    ZERO, TransactionExecution::getTotalConsideration, BigDecimal::add)));
  }

  private BigDecimal cashImpact(
      TransactionOrder order, @Nullable BigDecimal executedConsideration, LocalDate asOfDate) {
    if (executedConsideration != null && executedConsideration.signum() != 0) {
      return executedConsideration.abs();
    }
    return estimatedValue(order, asOfDate).abs();
  }

  private BigDecimal estimatedValue(TransactionOrder order, LocalDate asOfDate) {
    if (order.getInstrumentType() == ETF && order.getOrderQuantity() != null) {
      BigDecimal price = latestPrice(order.getInstrumentIsin(), asOfDate);
      if (price != null) {
        return order.getOrderQuantity().multiply(price);
      }
    }
    return order.getOrderAmount() == null ? ZERO : order.getOrderAmount();
  }

  @Nullable
  private BigDecimal latestPrice(String isin, LocalDate date) {
    return positionPriceResolver
        .resolve(isin, date)
        .map(resolved -> resolved.usedPrice())
        .filter(price -> price != null && price.signum() > 0)
        .orElse(null);
  }

  private static BigDecimal signed(TransactionOrder order, BigDecimal value) {
    return order.getTransactionType() == BUY ? value.abs() : value.abs().negate();
  }
}
