package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.SELL;
import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private enum InPositionReport {
    SHOWN_SO_THE_REPORT_CARRIES_ITS_CASH,
    NOT_YET_SHOWN_SO_THE_ORDER_CARRIES_ITS_CASH,
    MAYBE_SHOWN_SO_A_PURCHASE_IS_RESERVED_AND_A_SALE_NOT_COUNTED
  }

  private record ClassifiedFill(TransactionExecution execution, InPositionReport inPositionReport) {

    BigDecimal unreportedConsideration() {
      return notYetShown() ? absOrZero(execution.getTotalConsideration()) : ZERO;
    }

    BigDecimal unreportedQuantity() {
      return notYetShown() ? absOrZero(execution.getExecutedQuantity()) : ZERO;
    }

    BigDecimal cashTheOrderCarries(TransactionOrder order) {
      return cashToCount(order, inPositionReport, absOrZero(execution.getTotalConsideration()));
    }

    private boolean notYetShown() {
      return inPositionReport == InPositionReport.NOT_YET_SHOWN_SO_THE_ORDER_CARRIES_ITS_CASH;
    }
  }

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
      List<ClassifiedFill> fills = classifyAgainstPositionReport(order, executions, positionDate);
      BigDecimal unfilledValue = unfilledValue(order, executed, asOfDate);
      addUnreportedPositions(
          order, fills, executed, unfilledValue, unreportedValues, unreportedQuantities);
      BigDecimal cash =
          cashTheOrderStillCarries(
              order, fills, unfilledValue, unfilledRemainderInPositionReport(order, positionDate));
      if (order.getTransactionType() == BUY) {
        pendingBuys = pendingBuys.add(cash);
      } else {
        pendingSells = pendingSells.add(cash);
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

  private static List<ClassifiedFill> classifyAgainstPositionReport(
      TransactionOrder order, List<TransactionExecution> executions, LocalDate positionDate) {
    return executions.stream()
        .map(
            execution ->
                new ClassifiedFill(execution, inPositionReport(order, execution, positionDate)))
        .toList();
  }

  private static void addUnreportedPositions(
      TransactionOrder order,
      List<ClassifiedFill> fills,
      ExecutedTotals executed,
      BigDecimal unfilledValue,
      Map<String, BigDecimal> unreportedValues,
      Map<String, BigDecimal> unreportedQuantities) {
    String isin = order.getInstrumentIsin();
    for (ClassifiedFill fill : fills) {
      BigDecimal consideration = fill.unreportedConsideration();
      if (consideration.signum() != 0) {
        unreportedValues.merge(isin, signed(order, consideration), BigDecimal::add);
      }
      BigDecimal quantity = fill.unreportedQuantity();
      if (order.getInstrumentType() == ETF && quantity.signum() != 0) {
        unreportedQuantities.merge(isin, signed(order, quantity), BigDecimal::add);
      }
    }

    if (unfilledValue.signum() == 0) {
      return;
    }
    unreportedValues.merge(isin, signed(order, unfilledValue), BigDecimal::add);
    if (order.getTransactionType() == SELL) {
      addUnfilledQuantity(order, executed, isin, unreportedQuantities);
    }
  }

  private static void warnUndated(TransactionExecution execution, LocalDate positionDate) {
    log.warn(
        "Execution carries no reported date, leaving its position to the custodian report:"
            + " executionId={}, orderId={}, positionDate={}",
        execution.getId(),
        execution.getOrderId(),
        positionDate);
  }

  private static BigDecimal cashTheOrderStillCarries(
      TransactionOrder order,
      List<ClassifiedFill> fills,
      BigDecimal unfilledValue,
      InPositionReport unfilledRemainderInPositionReport) {
    BigDecimal fillsCash =
        fills.stream().map(fill -> fill.cashTheOrderCarries(order)).reduce(ZERO, BigDecimal::add);
    return fillsCash.add(cashToCount(order, unfilledRemainderInPositionReport, unfilledValue));
  }

  private static BigDecimal cashToCount(
      TransactionOrder order, InPositionReport inPositionReport, BigDecimal value) {
    return switch (inPositionReport) {
      case SHOWN_SO_THE_REPORT_CARRIES_ITS_CASH -> ZERO;
      case NOT_YET_SHOWN_SO_THE_ORDER_CARRIES_ITS_CASH -> value;
      case MAYBE_SHOWN_SO_A_PURCHASE_IS_RESERVED_AND_A_SALE_NOT_COUNTED ->
          order.getTransactionType() == BUY ? value : ZERO;
    };
  }

  private static void addUnfilledQuantity(
      TransactionOrder order,
      ExecutedTotals executed,
      String isin,
      Map<String, BigDecimal> unreportedQuantities) {
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

  private static InPositionReport inPositionReport(
      TransactionOrder order, TransactionExecution execution, LocalDate positionDate) {
    if (HISTORICAL_IMPORT_SOURCE.equals(execution.getSource())) {
      return InPositionReport.MAYBE_SHOWN_SO_A_PURCHASE_IS_RESERVED_AND_A_SALE_NOT_COUNTED;
    }
    LocalDate reportedDate = execution.getReportedDate();
    if (reportedDate == null) {
      warnUndated(execution, positionDate);
      return InPositionReport.MAYBE_SHOWN_SO_A_PURCHASE_IS_RESERVED_AND_A_SALE_NOT_COUNTED;
    }
    if (reportedDate.isAfter(positionDate)) {
      return InPositionReport.NOT_YET_SHOWN_SO_THE_ORDER_CARRIES_ITS_CASH;
    }
    if (reportedDate.isEqual(positionDate) && order.getInstrumentType() != ETF) {
      return fundFillTheReportMayBookOnlyTheNextDay();
    }
    return InPositionReport.SHOWN_SO_THE_REPORT_CARRIES_ITS_CASH;
  }

  private static InPositionReport fundFillTheReportMayBookOnlyTheNextDay() {
    return InPositionReport.MAYBE_SHOWN_SO_A_PURCHASE_IS_RESERVED_AND_A_SALE_NOT_COUNTED;
  }

  private static InPositionReport unfilledRemainderInPositionReport(
      TransactionOrder order, LocalDate positionDate) {
    return placedAfterTheReportDate(order, positionDate)
        ? InPositionReport.NOT_YET_SHOWN_SO_THE_ORDER_CARRIES_ITS_CASH
        : mayBeFilledUnseenOrStillAwaitingALaterFundNav();
  }

  private static boolean placedAfterTheReportDate(TransactionOrder order, LocalDate positionDate) {
    Instant placedAt = order.getOrderTimestamp();
    return placedAt != null && placedAt.atZone(TALLINN).toLocalDate().isAfter(positionDate);
  }

  private static InPositionReport mayBeFilledUnseenOrStillAwaitingALaterFundNav() {
    return InPositionReport.MAYBE_SHOWN_SO_A_PURCHASE_IS_RESERVED_AND_A_SALE_NOT_COUNTED;
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
