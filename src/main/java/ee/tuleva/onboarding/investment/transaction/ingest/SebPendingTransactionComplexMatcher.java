package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionType;
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

  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final SebClientNameToFundResolver fundResolver;

  Optional<TransactionOrder> match(SebPendingTransactionRow row) {
    if (row.isin() == null || row.side() == null) {
      return Optional.empty();
    }
    Optional<TulevaFund> fundOpt = fundResolver.resolve(row.clientName());
    if (fundOpt.isEmpty()) {
      log.info(
          "Complex match: unknown client name, no fund resolved: clientName={}, isin={}",
          row.clientName(),
          row.isin());
      return Optional.empty();
    }
    TulevaFund fund = fundOpt.get();

    List<TransactionOrder> candidates =
        orderRepository.findByInstrumentIsin(row.isin()).stream()
            .filter(o -> o.getFund() == fund)
            .filter(o -> o.getTransactionType() == row.side())
            .filter(o -> o.getOrderStatus() != OrderStatus.CANCELLED)
            .filter(this::isNotAlreadyLinkedToExecution)
            .filter(o -> quantityOrAmountMatches(o, row))
            .toList();

    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    if (candidates.size() > 1) {
      log.warn(
          "Complex match: ambiguous, refusing to match: clientName={}, isin={}, side={},"
              + " candidateOrderIds={}",
          row.clientName(),
          row.isin(),
          row.side(),
          candidates.stream().map(TransactionOrder::getId).toList());
      return Optional.empty();
    }
    return Optional.of(candidates.get(0));
  }

  private boolean isNotAlreadyLinkedToExecution(TransactionOrder order) {
    return executionRepository.findByOrderId(order.getId()).isEmpty();
  }

  private boolean quantityOrAmountMatches(TransactionOrder order, SebPendingTransactionRow row) {
    InstrumentType type = order.getInstrumentType();
    TransactionType side = order.getTransactionType();

    if (type == InstrumentType.ETF) {
      return quantityWithinTolerance(order.getOrderQuantity(), row.quantity());
    }
    // FUND
    if (side == TransactionType.BUY) {
      return amountWithinRelativeTolerance(order.getOrderAmount(), row.total());
    }
    // FUND SELL
    return quantityWithinTolerance(order.getOrderQuantity(), row.quantity());
  }

  private static boolean quantityWithinTolerance(Long orderQuantity, BigDecimal execQuantity) {
    if (orderQuantity == null || execQuantity == null) {
      return false;
    }
    BigDecimal diff = execQuantity.subtract(BigDecimal.valueOf(orderQuantity)).abs();
    return diff.compareTo(QUANTITY_TOLERANCE) < 0;
  }

  private static boolean amountWithinRelativeTolerance(
      BigDecimal orderAmount, BigDecimal execAmount) {
    if (orderAmount == null || execAmount == null) {
      return false;
    }
    if (execAmount.signum() == 0) {
      return false;
    }
    BigDecimal relativeDelta =
        execAmount.subtract(orderAmount).abs().divide(execAmount.abs(), MathContext.DECIMAL64);
    return relativeDelta.compareTo(FUND_BUY_AMOUNT_TOLERANCE) < 0;
  }
}
