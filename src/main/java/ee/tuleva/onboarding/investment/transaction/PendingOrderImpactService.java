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
import java.util.Set;
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

  private record ClassifiedFill(TransactionExecution execution, InPositionReport inPositionReport) {

    BigDecimal unreportedConsideration() {
      return notYetShown() ? absOrZero(execution.getTotalConsideration()) : ZERO;
    }

    BigDecimal unreportedQuantity() {
      return notYetShown() ? absOrZero(execution.getExecutedQuantity()) : ZERO;
    }

    BigDecimal cashTheOrderCarries(TransactionOrder order) {
      return inPositionReport.cashToCount(order, absOrZero(execution.getTotalConsideration()));
    }

    boolean bookedIn(Set<Long> executionIdsInTheBalance) {
      Long id = execution.getId();
      return id != null && executionIdsInTheBalance.contains(id);
    }

    private boolean notYetShown() {
      return inPositionReport == InPositionReport.NOT_YET_SHOWN_SO_THE_ORDER_CARRIES_ITS_CASH;
    }
  }

  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final PositionPriceResolver positionPriceResolver;

  PendingOrderImpact calculate(
      TulevaFund fund,
      LocalDate asOfDate,
      LocalDate positionDate,
      TradePrepPricing pricing,
      Set<Long> executionIdsInTheBalance) {
    UnreportedSales unreportedSales =
        salesTheReportDoesNotShow(fund, asOfDate, positionDate, pricing);
    List<TransactionOrder> unsettled = orderRepository.findUnsettledOrders(fund, asOfDate);
    if (unsettled.isEmpty()) {
      return PendingOrderImpact.none()
          .withUnreportedSales(unreportedSales.units(), unreportedSales.values());
    }

    Map<Long, List<TransactionExecution>> executionsByOrder = executionsByOrderId(unsettled);

    BigDecimal pendingBuys = ZERO;
    BigDecimal pendingSells = ZERO;
    BigDecimal buysInTheBalance = ZERO;
    BigDecimal sellsInTheBalance = ZERO;
    Map<String, BigDecimal> unreportedBuyValues = new HashMap<>();
    Map<String, BigDecimal> unreportedBuyQuantities = new HashMap<>();

    for (TransactionOrder order : unsettled) {
      List<TransactionExecution> executions =
          executionsByOrder.getOrDefault(order.getId(), List.of());
      ExecutedTotals executed = ExecutedTotals.of(executions);
      List<ClassifiedFill> fills = classifyAgainstPositionReport(order, executions, positionDate);
      BigDecimal unfilledValue = unfilledValue(order, executed, asOfDate, pricing);
      List<ClassifiedFill> booked =
          fills.stream().filter(fill -> fill.bookedIn(executionIdsInTheBalance)).toList();
      List<ClassifiedFill> notYetBooked =
          fills.stream().filter(fill -> !fill.bookedIn(executionIdsInTheBalance)).toList();
      BigDecimal cash =
          cashTheOrderStillCarries(
              order,
              notYetBooked,
              unfilledValue,
              InPositionReport.ofUnfilledRemainder(order, positionDate));
      BigDecimal inTheBalance = cashOfFills(order, booked);
      if (order.getTransactionType() == BUY) {
        addUnreportedPurchase(
            order, fills, unfilledValue, unreportedBuyValues, unreportedBuyQuantities);
        pendingBuys = pendingBuys.add(cash);
        buysInTheBalance = buysInTheBalance.add(inTheBalance);
      } else {
        pendingSells = pendingSells.add(cash);
        sellsInTheBalance = sellsInTheBalance.add(inTheBalance);
      }
    }

    log.info(
        "Pending order impact: fund={}, asOfDate={}, orderCount={}, pendingBuys={},"
            + " pendingSells={}, unreportedBuyIsins={}, unreportedSellIsins={}",
        fund,
        asOfDate,
        unsettled.size(),
        pendingBuys.toPlainString(),
        pendingSells.toPlainString(),
        unreportedBuyValues.keySet(),
        unreportedSales.units().keySet());

    return new PendingOrderImpact(
        pendingBuys,
        pendingSells,
        Map.copyOf(unreportedBuyValues),
        Map.copyOf(unreportedBuyQuantities),
        buysInTheBalance,
        sellsInTheBalance,
        unreportedSales.units(),
        unreportedSales.values());
  }

  private record UnreportedSales(Map<String, BigDecimal> units, Map<String, BigDecimal> values) {

    static final UnreportedSales NONE = new UnreportedSales(Map.of(), Map.of());
  }

  private UnreportedSales salesTheReportDoesNotShow(
      TulevaFund fund, LocalDate asOfDate, LocalDate positionDate, TradePrepPricing pricing) {
    List<TransactionOrder> sales =
        orderRepository.findSalesTheReportMayNotShowYet(fund, positionDate);
    if (sales.isEmpty()) {
      return UnreportedSales.NONE;
    }
    Map<Long, List<TransactionExecution>> executionsByOrder = executionsByOrderId(sales);
    Map<String, BigDecimal> units = new HashMap<>();
    Map<String, BigDecimal> values = new HashMap<>();
    for (TransactionOrder sale : sales) {
      List<TransactionExecution> executions =
          executionsByOrder.getOrDefault(sale.getId(), List.of());
      List<ClassifiedFill> fills = classifyAgainstPositionReport(sale, executions, positionDate);
      ExecutedTotals executed = ExecutedTotals.of(executions);
      boolean stillOpen = sale.getOrderStatus() == OrderStatus.SENT;
      units.merge(
          sale.getInstrumentIsin(),
          fills.stream()
              .map(ClassifiedFill::unreportedQuantity)
              .reduce(stillOpen ? unfilledQuantity(sale, executed) : ZERO, BigDecimal::add),
          BigDecimal::add);
      values.merge(
          sale.getInstrumentIsin(),
          fills.stream()
              .map(ClassifiedFill::unreportedConsideration)
              .reduce(
                  stillOpen ? unfilledValue(sale, executed, asOfDate, pricing) : ZERO,
                  BigDecimal::add),
          BigDecimal::add);
    }
    return new UnreportedSales(Map.copyOf(units), Map.copyOf(values));
  }

  private static List<ClassifiedFill> classifyAgainstPositionReport(
      TransactionOrder order, List<TransactionExecution> executions, LocalDate positionDate) {
    return executions.stream()
        .map(
            execution ->
                new ClassifiedFill(
                    execution, InPositionReport.ofFill(order, execution, positionDate)))
        .toList();
  }

  private static void addUnreportedPurchase(
      TransactionOrder order,
      List<ClassifiedFill> fills,
      BigDecimal unfilledValue,
      Map<String, BigDecimal> unreportedValues,
      Map<String, BigDecimal> unreportedQuantities) {
    String isin = order.getInstrumentIsin();
    addUnreportedValue(isin, fills, unfilledValue, unreportedValues);
    BigDecimal quantity =
        fills.stream().map(ClassifiedFill::unreportedQuantity).reduce(ZERO, BigDecimal::add);
    if (order.getInstrumentType() == ETF && quantity.signum() != 0) {
      unreportedQuantities.merge(isin, quantity, BigDecimal::add);
    }
  }

  private static void addUnreportedValue(
      String isin,
      List<ClassifiedFill> fills,
      BigDecimal unfilledValue,
      Map<String, BigDecimal> values) {
    BigDecimal value =
        fills.stream()
            .map(ClassifiedFill::unreportedConsideration)
            .reduce(unfilledValue, BigDecimal::add);
    if (value.signum() != 0) {
      values.merge(isin, value, BigDecimal::add);
    }
  }

  private static BigDecimal cashTheOrderStillCarries(
      TransactionOrder order,
      List<ClassifiedFill> fills,
      BigDecimal unfilledValue,
      InPositionReport unfilledRemainderInPositionReport) {
    return cashOfFills(order, fills)
        .add(unfilledRemainderInPositionReport.cashToCount(order, unfilledValue));
  }

  private static BigDecimal cashOfFills(TransactionOrder order, List<ClassifiedFill> fills) {
    return fills.stream()
        .map(fill -> fill.cashTheOrderCarries(order))
        .reduce(ZERO, BigDecimal::add);
  }

  private static BigDecimal unfilledQuantity(TransactionOrder order, ExecutedTotals executed) {
    BigDecimal orderQuantity = order.getOrderQuantity();
    return orderQuantity == null
        ? ZERO
        : orderQuantity.abs().subtract(executed.quantity()).max(ZERO);
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
      TransactionOrder order,
      ExecutedTotals executed,
      LocalDate asOfDate,
      TradePrepPricing pricing) {
    BigDecimal orderQuantity = order.getOrderQuantity();
    if (orderQuantity == null) {
      return unfilledAmount(order, executed);
    }
    BigDecimal unfilledQuantity = unfilledQuantity(order, executed);
    if (unfilledQuantity.signum() == 0) {
      return ZERO;
    }
    BigDecimal price = latestPrice(order.getInstrumentIsin(), asOfDate, pricing);
    return price == null ? unfilledAmount(order, executed) : unfilledQuantity.multiply(price);
  }

  private static BigDecimal unfilledAmount(TransactionOrder order, ExecutedTotals executed) {
    BigDecimal orderAmount = order.getOrderAmount();
    return orderAmount == null
        ? ZERO
        : orderAmount.abs().subtract(executed.consideration()).max(ZERO);
  }

  @Nullable
  private BigDecimal latestPrice(String isin, LocalDate date, TradePrepPricing pricing) {
    return pricing
        .resolve(positionPriceResolver, isin, date)
        .map(TradePrepPrice::value)
        .filter(price -> price != null && price.signum() > 0)
        .orElse(null);
  }
}
