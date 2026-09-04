package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
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

  private static final String HISTORICAL_IMPORT_SOURCE = "HISTORICAL_IMPORT";

  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final PositionPriceResolver positionPriceResolver;

  PendingOrderImpact calculate(TulevaFund fund, LocalDate asOfDate, LocalDate positionDate) {
    List<TransactionOrder> unsettled = orderRepository.findUnsettledOrders(fund, asOfDate);
    if (unsettled.isEmpty()) {
      return PendingOrderImpact.none();
    }

    Map<Long, List<TransactionExecution>> executionsByOrder = executionsByOrderId(unsettled);

    BigDecimal pendingBuys = ZERO;
    BigDecimal pendingSells = ZERO;
    Map<String, BigDecimal> unreportedValues = new HashMap<>();
    Map<String, BigDecimal> unreportedQuantities = new HashMap<>();

    for (TransactionOrder order : unsettled) {
      List<TransactionExecution> executions =
          executionsByOrder.getOrDefault(order.getId(), List.of());
      ExecutedTotals executed = ExecutedTotals.of(executions);
      BigDecimal cashImpact = expectedConsideration(order, executed, asOfDate);
      if (order.getTransactionType() == BUY) {
        pendingBuys = pendingBuys.add(cashImpact);
      } else {
        pendingSells = pendingSells.add(cashImpact);
      }

      addUnreportedPositions(
          order,
          executions,
          executed,
          positionDate,
          asOfDate,
          unreportedValues,
          unreportedQuantities);
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

  private void addUnreportedPositions(
      TransactionOrder order,
      List<TransactionExecution> executions,
      ExecutedTotals executed,
      LocalDate positionDate,
      LocalDate asOfDate,
      Map<String, BigDecimal> unreportedValues,
      Map<String, BigDecimal> unreportedQuantities) {
    String isin = order.getInstrumentIsin();
    for (TransactionExecution execution : executions) {
      if (!isMissingFromPositionReport(execution, positionDate)) {
        continue;
      }
      BigDecimal consideration = absOrZero(execution.getTotalConsideration());
      if (consideration.signum() != 0) {
        unreportedValues.merge(isin, signed(order, consideration), BigDecimal::add);
      }
      BigDecimal quantity = absOrZero(execution.getExecutedQuantity());
      if (order.getInstrumentType() == ETF && quantity.signum() != 0) {
        unreportedQuantities.merge(isin, signed(order, quantity), BigDecimal::add);
      }
    }

    BigDecimal unfilledValue = unfilledValue(order, executed, asOfDate);
    if (unfilledValue.signum() == 0) {
      return;
    }
    unreportedValues.merge(isin, signed(order, unfilledValue), BigDecimal::add);
    addUnfilledSellQuantity(order, executed, isin, unreportedQuantities);
  }

  private static void addUnfilledSellQuantity(
      TransactionOrder order,
      ExecutedTotals executed,
      String isin,
      Map<String, BigDecimal> unreportedQuantities) {
    if (order.getTransactionType() == BUY) {
      return;
    }
    BigDecimal unfilledQuantity = unfilledQuantity(order, executed);
    if (order.getInstrumentType() == ETF && unfilledQuantity.signum() != 0) {
      unreportedQuantities.merge(isin, signed(order, unfilledQuantity), BigDecimal::add);
    }
  }

  private static BigDecimal unfilledQuantity(TransactionOrder order, ExecutedTotals executed) {
    BigDecimal orderQuantity = order.getOrderQuantity();
    return orderQuantity == null
        ? ZERO
        : orderQuantity.abs().subtract(executed.quantity()).max(ZERO);
  }

  private static boolean isMissingFromPositionReport(
      TransactionExecution execution, LocalDate positionDate) {
    if (HISTORICAL_IMPORT_SOURCE.equals(execution.getSource())) {
      return false;
    }
    LocalDate reportedDate = execution.getReportedDate();
    if (reportedDate == null) {
      log.warn(
          "Execution carries no reported date, leaving its position to the custodian report:"
              + " executionId={}, orderId={}, positionDate={}",
          execution.getId(),
          execution.getOrderId(),
          positionDate);
      return false;
    }
    return reportedDate.isAfter(positionDate);
  }

  private record ExecutedTotals(BigDecimal consideration, BigDecimal quantity) {

    static final ExecutedTotals NONE = new ExecutedTotals(ZERO, ZERO);

    static ExecutedTotals of(List<TransactionExecution> executions) {
      ExecutedTotals totals = NONE;
      for (TransactionExecution execution : executions) {
        totals = totals.add(execution);
      }
      return totals;
    }

    ExecutedTotals add(TransactionExecution execution) {
      return new ExecutedTotals(
          consideration.add(absOrZero(execution.getTotalConsideration())),
          quantity.add(absOrZero(execution.getExecutedQuantity())));
    }
  }

  private static BigDecimal absOrZero(@Nullable BigDecimal value) {
    return value == null ? ZERO : value.abs();
  }

  private Map<Long, List<TransactionExecution>> executionsByOrderId(List<TransactionOrder> orders) {
    List<Long> orderIds =
        orders.stream().map(TransactionOrder::getId).filter(Objects::nonNull).toList();
    return executionRepository.findByOrderIdIn(orderIds).stream()
        .collect(Collectors.groupingBy(TransactionExecution::getOrderId));
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
    BigDecimal unfilledQuantity = unfilledQuantity(order, executed);
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
