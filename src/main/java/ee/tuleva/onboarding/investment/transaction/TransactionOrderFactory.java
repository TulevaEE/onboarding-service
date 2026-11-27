package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;

import ee.tuleva.onboarding.comparisons.fundvalue.PositionPriceResolver;
import ee.tuleva.onboarding.comparisons.fundvalue.ResolvedPrice;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.IndexAdjustedPrice;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.IndexPrice;
import ee.tuleva.onboarding.investment.transaction.TradePrepPrice.ManualPrice;
import java.math.BigDecimal;
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
      TransactionBatch batch,
      FundCalculationResult result,
      LocalDate asOfDate,
      Instant createdAt,
      TradePrepPricing pricing) {
    var input = result.input();
    List<TransactionOrder> orders = new ArrayList<>();
    SaleSizing saleSizing = new SaleSizing(result.fund(), input.sellableUnits(), input.positions());
    Map<String, @Nullable TradePrepPrice> priceResolutions = new LinkedHashMap<>();

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
      var engineAmount = trade.tradeAmount().abs();
      var sized =
          saleSizing.size(
              transactionType,
              instrumentType,
              isin,
              engineAmount,
              resolveOrderQuantity(
                  instrumentType, transactionType, isin, engineAmount, asOfDate, pricing));
      if (sized.isEmpty()) {
        continue;
      }
      var orderAmount = sized.get().amount();
      var orderQuantity = sized.get().quantity();
      BigDecimal quantity = orderQuantity.quantity();
      if (quantity != null && quantity.signum() == 0) {
        log.warn(
            "Skipping an order that rounds to zero units: fund={}, isin={}, side={}, amount={}",
            result.fund(),
            isin,
            transactionType,
            orderAmount.toPlainString());
        continue;
      }
      if (!isAmountBasedOrder(instrumentType, transactionType)) {
        priceResolutions.put(isin, orderQuantity.price());
      }
      orders.add(
          TransactionOrder.builder()
              .batch(batch)
              .fund(result.fund())
              .instrumentIsin(isin)
              .transactionType(transactionType)
              .instrumentType(instrumentType)
              .orderAmount(orderAmount)
              .orderQuantity(quantity)
              .comment(orderQuantity.stalePriceComment())
              .orderVenue(input.orderVenues().getOrDefault(isin, OrderVenue.SEB))
              .createdAt(createdAt)
              .build());
    }
    return new CalculatedOrders(orders, priceResolutions, saleSizing.sizedSales());
  }

  void requireQuantitiesForNonAmountOrders(TransactionBatch batch, List<TransactionOrder> orders) {
    List<String> missingQuantity = new ArrayList<>();
    for (TransactionOrder order : orders) {
      if (isAmountBasedOrder(order.getInstrumentType(), order.getTransactionType())) {
        continue;
      }
      if (!OrderUnits.fitsAnOrder(order.getInstrumentType(), order.getOrderQuantity())) {
        missingQuantity.add(order.getInstrumentIsin());
      }
    }
    if (!missingQuantity.isEmpty()) {
      throw new TransactionBatchRefusedException(
          "Cannot finalize batch: orders need a positive quantity, in whole units for an ETF:"
              + " batchId="
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
      LocalDate asOfDate,
      TradePrepPricing pricing) {
    if (isAmountBasedOrder(instrumentType, transactionType)) {
      return new OrderQuantity(null, null, null);
    }
    TradePrepPrice price = pricing.resolve(positionPriceResolver, isin, asOfDate).orElse(null);
    if (price == null) {
      log.warn(
          "No price found for order quantity: isin={}, instrumentType={}, asOfDate={}",
          isin,
          instrumentType,
          asOfDate);
      return new OrderQuantity(null, null, null);
    }
    BigDecimal value = price.value();
    if (value == null) {
      log.warn(
          "No price found for order quantity: isin={}, instrumentType={}, asOfDate={}",
          isin,
          instrumentType,
          asOfDate);
      return new OrderQuantity(null, null, price);
    }
    if (value.signum() <= 0) {
      log.warn(
          "Non-positive price for order quantity, leaving quantity unset:"
              + " isin={}, instrumentType={}, price={}, asOfDate={}",
          isin,
          instrumentType,
          value.toPlainString(),
          asOfDate);
      return new OrderQuantity(null, null, price);
    }
    BigDecimal quantity = OrderUnits.of(instrumentType, transactionType, orderAmount, value);
    String stalePriceComment =
        switch (price) {
          case IndexPrice index ->
              describeIfStale(isin, index.resolved(), pricing.stalenessReference(asOfDate));
          case IndexAdjustedPrice adjusted ->
              describeIfStale(isin, adjusted.fundPrice(), adjusted.positionDate());
          case ManualPrice _ -> null;
        };
    return new OrderQuantity(quantity, stalePriceComment, price);
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
