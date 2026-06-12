package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SebPendingTransactionComplexMatcher {

  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final SebClientNameToFundResolver fundResolver;
  private final QuantityAmountValidator quantityAmountValidator;

  Optional<TransactionOrder> match(
      SebPendingTransactionRow row, TransactionMatchingProperties properties) {
    List<TransactionOrder> candidates = sameFundIsinSideCandidates(row);
    if (candidates == null) {
      return Optional.empty();
    }
    List<TransactionOrder> inTolerance =
        candidates.stream()
            .filter(o -> quantityAmountValidator.withinTolerance(o, row, properties))
            .toList();

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

  // True if the row has any same-fund+ISIN+side order within the near-miss band — including the
  // ambiguous case where findNearMiss returns empty because there is more than one candidate. The
  // settlement digest uses this so a near-miss row is treated as a mismatch, not as "unmatched".
  boolean hasNearMissCandidate(
      SebPendingTransactionRow row, TransactionMatchingProperties properties) {
    List<TransactionOrder> candidates = sameFundIsinSideCandidates(row);
    if (candidates == null) {
      return false;
    }
    return candidates.stream()
        .anyMatch(candidate -> quantityAmountValidator.withinNearMiss(candidate, row, properties));
  }

  Optional<QuantityAmountMismatchEvent> findNearMiss(
      SebPendingTransactionRow row, TransactionMatchingProperties properties) {
    List<TransactionOrder> candidates = sameFundIsinSideCandidates(row);
    if (candidates == null) {
      return Optional.empty();
    }
    // Only consider candidates that are NOT already a clean in-tolerance match —
    // a clean match would have been picked up by match() and is not a near miss.
    List<TransactionOrder> nearMissCandidates =
        candidates.stream()
            .filter(o -> !quantityAmountValidator.withinTolerance(o, row, properties))
            .filter(o -> quantityAmountValidator.withinNearMiss(o, row, properties))
            .toList();

    if (nearMissCandidates.size() != 1) {
      return Optional.empty();
    }
    TransactionOrder order = nearMissCandidates.get(0);
    return Optional.of(quantityAmountValidator.buildMismatchEvent(order, row, properties));
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
}
