package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.fund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  private static final ZoneId TALLINN = ZoneId.of(TIMEZONE);

  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final PositionPriceResolver positionPriceResolver;

  PendingOrderImpact calculate(TulevaFund fund, LocalDate asOfDate, LocalDate positionDate) {
    List<TransactionOrder> unsettled = orderRepository.findUnsettledOrders(fund, asOfDate);
    if (unsettled.isEmpty()) {
      return PendingOrderImpact.none();
    }

    Map<Long, ExecutedTotals> executed = executedTotalsByOrderId(unsettled);

    BigDecimal pendingBuys = ZERO;
    BigDecimal pendingSells = ZERO;
    Map<String, BigDecimal> unreportedValues = new HashMap<>();
    Map<String, BigDecimal> unreportedQuantities = new HashMap<>();

    for (TransactionOrder order : unsettled) {
      BigDecimal cashImpact = expectedConsideration(order, executedOf(executed, order), asOfDate);
      if (order.getTransactionType() == BUY) {
        pendingBuys = pendingBuys.add(cashImpact);
      } else {
        pendingSells = pendingSells.add(cashImpact);
      }

      if (cashImpact.signum() == 0 || !isMissingFromPositionReport(order, positionDate)) {
        continue;
      }
      unreportedValues.merge(order.getInstrumentIsin(), signed(order, cashImpact), BigDecimal::add);
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

  private static boolean isMissingFromPositionReport(
      TransactionOrder order, LocalDate positionDate) {
    Instant orderTimestamp = order.getOrderTimestamp();
    return orderTimestamp != null
        && orderTimestamp.atZone(TALLINN).toLocalDate().isAfter(positionDate);
  }

  private record ExecutedTotals(BigDecimal consideration, BigDecimal quantity) {

    static final ExecutedTotals NONE = new ExecutedTotals(ZERO, ZERO);

    ExecutedTotals add(TransactionExecution execution) {
      return new ExecutedTotals(
          consideration.add(absOrZero(execution.getTotalConsideration())),
          quantity.add(absOrZero(execution.getExecutedQuantity())));
    }

    private static BigDecimal absOrZero(@Nullable BigDecimal value) {
      return value == null ? ZERO : value.abs();
    }
  }

  private Map<Long, ExecutedTotals> executedTotalsByOrderId(List<TransactionOrder> orders) {
    List<Long> orderIds =
        orders.stream().map(TransactionOrder::getId).filter(Objects::nonNull).toList();
    Map<Long, ExecutedTotals> totals = new HashMap<>();
    executionRepository
        .findByOrderIdIn(orderIds)
        .forEach(
            execution ->
                totals.compute(
                    execution.getOrderId(),
                    (orderId, accumulated) ->
                        (accumulated == null ? ExecutedTotals.NONE : accumulated).add(execution)));
    return totals;
  }

  private static ExecutedTotals executedOf(
      Map<Long, ExecutedTotals> executed, TransactionOrder order) {
    return executed.getOrDefault(order.getId(), ExecutedTotals.NONE);
  }

  private BigDecimal expectedConsideration(
      TransactionOrder order, ExecutedTotals executed, LocalDate asOfDate) {
    return executed.consideration().add(unfilledValue(order, executed, asOfDate));
  }

  private BigDecimal unfilledValue(
      TransactionOrder order, ExecutedTotals executed, LocalDate asOfDate) {
    BigDecimal orderQuantity = order.getOrderQuantity();
    if (orderQuantity == null) {
      return unfilledAmount(order, executed);
    }
    BigDecimal unfilledQuantity = orderQuantity.abs().subtract(executed.quantity()).max(ZERO);
    if (unfilledQuantity.signum() == 0) {
      return ZERO;
    }
    BigDecimal price = latestPrice(order.getInstrumentIsin(), asOfDate);
    return price == null ? unfilledAmount(order, executed) : unfilledQuantity.multiply(price);
  }

  private static BigDecimal unfilledAmount(TransactionOrder order, ExecutedTotals executed) {
    BigDecimal orderAmount = order.getOrderAmount();
    return orderAmount == null
        ? ZERO
        : orderAmount.abs().subtract(executed.consideration()).max(ZERO);
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
