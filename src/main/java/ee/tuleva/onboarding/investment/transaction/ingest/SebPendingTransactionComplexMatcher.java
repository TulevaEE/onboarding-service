package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.investment.transaction.ingest.QuantityAmountMismatchEvent.MismatchKind;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SebPendingTransactionComplexMatcher {

  private static final BigDecimal QUANTITY_TOLERANCE = new BigDecimal("0.0001");
  private static final BigDecimal FUND_BUY_AMOUNT_TOLERANCE = new BigDecimal("0.02"); // 2%
  private static final BigDecimal NEAR_MISS_MULTIPLIER = new BigDecimal("5");

  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final SebClientNameToFundResolver fundResolver;

  Optional<TransactionOrder> match(SebPendingTransactionRow row) {
    List<TransactionOrder> candidates = sameFundIsinSideCandidates(row);
    if (candidates == null) {
      return Optional.empty();
    }
    List<TransactionOrder> inTolerance =
        candidates.stream().filter(o -> quantityOrAmountMatches(o, row)).toList();

    if (inTolerance.isEmpty()) {
      return Optional.empty();
    }
    if (inTolerance.size() > 1) {
      log.warn(
          "Complex match: ambiguous, refusing to match: clientName={}, isin={}, side={},"
              + " candidateOrderIds={}",
          row.clientName(),
          row.isin(),
          row.side(),
          inTolerance.stream().map(TransactionOrder::getId).toList());
      return Optional.empty();
    }
    return Optional.of(inTolerance.get(0));
  }

  Optional<QuantityAmountMismatchEvent> findNearMiss(SebPendingTransactionRow row) {
    List<TransactionOrder> candidates = sameFundIsinSideCandidates(row);
    if (candidates == null) {
      return Optional.empty();
    }
    // Only consider candidates that are NOT already a clean in-tolerance match —
    // a clean match would have been picked up by match() and is not a near miss.
    List<TransactionOrder> nearMissCandidates =
        candidates.stream()
            .filter(o -> !quantityOrAmountMatches(o, row))
            .filter(o -> quantityOrAmountWithinNearMiss(o, row))
            .toList();

    if (nearMissCandidates.size() != 1) {
      return Optional.empty();
    }
    TransactionOrder order = nearMissCandidates.get(0);
    return Optional.of(buildMismatchEvent(order, row));
  }

  private List<TransactionOrder> sameFundIsinSideCandidates(SebPendingTransactionRow row) {
    if (row.isin() == null || row.side() == null) {
      return null;
    }
    Optional<TulevaFund> fundOpt = fundResolver.resolve(row.clientName());
    if (fundOpt.isEmpty()) {
      log.info(
          "Complex match: unknown client name, no fund resolved: clientName={}, isin={}",
          row.clientName(),
          row.isin());
      return null;
    }
    TulevaFund fund = fundOpt.get();
    return orderRepository.findByInstrumentIsin(row.isin()).stream()
        .filter(o -> o.getFund() == fund)
        .filter(o -> o.getTransactionType() == row.side())
        .filter(o -> o.getOrderStatus() != OrderStatus.CANCELLED)
        .filter(this::isNotAlreadyLinkedToExecution)
        .toList();
  }

  private boolean isNotAlreadyLinkedToExecution(TransactionOrder order) {
    return executionRepository.findByOrderId(order.getId()).isEmpty();
  }

  private boolean quantityOrAmountMatches(TransactionOrder order, SebPendingTransactionRow row) {
    return withinTolerance(order, row, BigDecimal.ONE);
  }

  private boolean quantityOrAmountWithinNearMiss(
      TransactionOrder order, SebPendingTransactionRow row) {
    return withinTolerance(order, row, NEAR_MISS_MULTIPLIER);
  }

  private static boolean withinTolerance(
      TransactionOrder order, SebPendingTransactionRow row, BigDecimal multiplier) {
    InstrumentType type = order.getInstrumentType();
    TransactionType side = order.getTransactionType();

    if (type == InstrumentType.ETF) {
      return quantityWithinTolerance(
          order.getOrderQuantity(), row.quantity(), QUANTITY_TOLERANCE.multiply(multiplier));
    }
    if (side == TransactionType.BUY) {
      return amountWithinRelativeTolerance(
          order.getOrderAmount(), row.total(), FUND_BUY_AMOUNT_TOLERANCE.multiply(multiplier));
    }
    return quantityWithinTolerance(
        order.getOrderQuantity(), row.quantity(), QUANTITY_TOLERANCE.multiply(multiplier));
  }

  private static boolean quantityWithinTolerance(
      Long orderQuantity, BigDecimal execQuantity, BigDecimal tolerance) {
    if (orderQuantity == null || execQuantity == null) {
      return false;
    }
    BigDecimal diff = execQuantity.subtract(BigDecimal.valueOf(orderQuantity)).abs();
    return diff.compareTo(tolerance) < 0;
  }

  private static boolean amountWithinRelativeTolerance(
      BigDecimal orderAmount, BigDecimal execAmount, BigDecimal tolerance) {
    if (orderAmount == null || execAmount == null) {
      return false;
    }
    if (execAmount.signum() == 0) {
      return false;
    }
    BigDecimal relativeDelta =
        execAmount.subtract(orderAmount).abs().divide(execAmount.abs(), MathContext.DECIMAL64);
    return relativeDelta.compareTo(tolerance) < 0;
  }

  private static QuantityAmountMismatchEvent buildMismatchEvent(
      TransactionOrder order, SebPendingTransactionRow row) {
    InstrumentType type = order.getInstrumentType();
    TransactionType side = order.getTransactionType();
    if (type == InstrumentType.ETF) {
      BigDecimal expected = BigDecimal.valueOf(order.getOrderQuantity());
      BigDecimal actual = row.quantity();
      return new QuantityAmountMismatchEvent(
          row,
          order,
          MismatchKind.ETF_QUANTITY,
          expected,
          actual,
          actual.subtract(expected).abs(),
          null);
    }
    if (side == TransactionType.BUY) {
      BigDecimal expected = order.getOrderAmount();
      BigDecimal actual = row.total();
      return new QuantityAmountMismatchEvent(
          row,
          order,
          MismatchKind.FUND_BUY_AMOUNT,
          expected,
          actual,
          actual.subtract(expected).abs(),
          null);
    }
    BigDecimal expected = BigDecimal.valueOf(order.getOrderQuantity());
    BigDecimal actual = row.quantity();
    return new QuantityAmountMismatchEvent(
        row,
        order,
        MismatchKind.FUND_SELL_QUANTITY,
        expected,
        actual,
        actual.subtract(expected).abs(),
        null);
  }
}
