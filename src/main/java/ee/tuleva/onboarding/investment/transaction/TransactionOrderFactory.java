package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionOrderFactory {

  private static final int STALE_PRICE_THRESHOLD_DAYS = 3;

  private final PositionPriceResolver positionPriceResolver;

  CalculatedOrders createOrders(
      TransactionBatch batch, FundCalculationResult result, LocalDate asOfDate, Instant createdAt) {
    var input = result.input();
    List<TransactionOrder> orders = new ArrayList<>();
    Map<String, @Nullable ResolvedPrice> priceResolutions = new LinkedHashMap<>();

    for (TradeCalculation trade : result.trades()) {
      if (trade.tradeAmount().compareTo(ZERO) == 0) {
        continue;
      }
      String isin =
          Objects.requireNonNull(
              trade.isin(), "Trade with non-zero amount is missing isin: fund=" + result.fund());
      var instrumentType = input.instrumentTypes().getOrDefault(isin, InstrumentType.ETF);
      var transactionType =
          trade.tradeAmount().compareTo(ZERO) > 0 ? TransactionType.BUY : TransactionType.SELL;
      var orderAmount = trade.tradeAmount().abs();
      var orderQuantity =
          resolveOrderQuantity(instrumentType, transactionType, isin, orderAmount, asOfDate);
      if (!isAmountBasedOrder(instrumentType, transactionType)) {
        priceResolutions.put(isin, orderQuantity.resolvedPrice());
      }
      orders.add(
          TransactionOrder.builder()
              .batch(batch)
              .fund(result.fund())
              .instrumentIsin(isin)
              .transactionType(transactionType)
              .instrumentType(instrumentType)
              .orderAmount(orderAmount)
              .orderQuantity(orderQuantity.quantity())
              .comment(orderQuantity.stalePriceComment())
              .orderVenue(input.orderVenues().getOrDefault(isin, OrderVenue.SEB))
              .createdAt(createdAt)
              .build());
    }
    return new CalculatedOrders(orders, priceResolutions);
  }

  void requireQuantitiesForNonAmountOrders(TransactionBatch batch, List<TransactionOrder> orders) {
    List<String> missingQuantity = new ArrayList<>();
    for (TransactionOrder order : orders) {
      if (isAmountBasedOrder(order.getInstrumentType(), order.getTransactionType())) {
        continue;
      }
      if (order.getOrderQuantity() == null) {
        missingQuantity.add(order.getInstrumentIsin());
      }
    }
    if (!missingQuantity.isEmpty()) {
      throw new IllegalStateException(
          "Cannot finalize batch: orders require a quantity but have none: batchId="
              + batch.getId()
              + ", isins="
              + missingQuantity);
    }
  }

  private OrderQuantity resolveOrderQuantity(
      InstrumentType instrumentType,
      TransactionType transactionType,
      String isin,
      BigDecimal orderAmount,
      LocalDate asOfDate) {
    if (isAmountBasedOrder(instrumentType, transactionType)) {
      return new OrderQuantity(null, null, null);
    }
    ResolvedPrice resolvedPrice = positionPriceResolver.resolve(isin, asOfDate).orElse(null);
    if (resolvedPrice == null) {
      log.warn(
          "No price found for order quantity: isin={}, instrumentType={}, asOfDate={}",
          isin,
          instrumentType,
          asOfDate);
      return new OrderQuantity(null, null, null);
    }
    BigDecimal price = resolvedPrice.usedPrice();
    if (price == null) {
      log.warn(
          "No price found for order quantity: isin={}, instrumentType={}, asOfDate={}",
          isin,
          instrumentType,
          asOfDate);
      return new OrderQuantity(null, null, resolvedPrice);
    }
    if (price.signum() <= 0) {
      log.warn(
          "Non-positive price for order quantity, leaving quantity unset:"
              + " isin={}, instrumentType={}, price={}, asOfDate={}",
          isin,
          instrumentType,
          price.toPlainString(),
          asOfDate);
      return new OrderQuantity(null, null, resolvedPrice);
    }
    BigDecimal quantity = orderAmount.divide(price, 6, RoundingMode.HALF_UP);
    String stalePriceComment = describeIfStale(isin, resolvedPrice, asOfDate);
    return new OrderQuantity(quantity, stalePriceComment, resolvedPrice);
  }

  @Nullable
  private String describeIfStale(String isin, ResolvedPrice resolvedPrice, LocalDate asOfDate) {
    LocalDate priceDate = resolvedPrice.priceDate();
    if (priceDate == null) {
      return null;
    }
    long ageDays = ChronoUnit.DAYS.between(priceDate, asOfDate);
    if (ageDays <= STALE_PRICE_THRESHOLD_DAYS) {
      return null;
    }
    log.warn(
        "Order sized on stale price: isin={}, priceDate={}, ageDays={}, source={}, asOfDate={}",
        isin,
        priceDate,
        ageDays,
        resolvedPrice.priceSource(),
        asOfDate);
    return "Sized on stale price: priceDate=%s, ageDays=%d, source=%s"
        .formatted(priceDate, ageDays, resolvedPrice.priceSource());
  }

  private boolean isAmountBasedOrder(
      InstrumentType instrumentType, TransactionType transactionType) {
    return instrumentType == InstrumentType.FUND && transactionType == TransactionType.BUY;
  }
}
