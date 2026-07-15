package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class UnmatchedPendingTransactionFinder {

  private final SebPendingTransactionExtractor extractor;
  private final SebPendingTransactionMatcher matcher;
  private final SebPendingTransactionComplexMatcher complexMatcher;
  private final TransactionMatchingPolicy matchingPolicy;
  private final TransactionOrderRepository orderRepository;
  private final TransactionExecutionRepository executionRepository;
  private final SebClientNameToFundResolver fundResolver;

  record InconsistentMatchedRow(
      SebPendingTransactionRow row, TransactionOrder order, String reason) {}

  List<SebPendingTransactionRow> collectUnmatched(InvestmentReport report) {
    TransactionMatchingProperties matchingProperties = matchingPolicy.current();
    return extractor.extract(report).stream()
        .filter(row -> !isAlreadyLinkedToExecution(row))
        .filter(row -> matcher.match(row).isEmpty())
        .filter(row -> complexMatcher.match(row, matchingProperties).isEmpty())
        .filter(row -> !complexMatcher.hasNearMissCandidate(row, matchingProperties))
        .toList();
  }

  List<InconsistentMatchedRow> collectInconsistent(InvestmentReport report) {
    List<InconsistentMatchedRow> inconsistent = new ArrayList<>();
    for (SebPendingTransactionRow row : extractor.extract(report)) {
      if (row.clientRef() == null) {
        continue;
      }
      orderRepository
          .findByOrderUuid(row.clientRef())
          .ifPresent(
              order ->
                  inconsistencyReason(order, row)
                      .ifPresent(
                          reason ->
                              inconsistent.add(new InconsistentMatchedRow(row, order, reason))));
    }
    return inconsistent;
  }

  private Optional<String> inconsistencyReason(
      TransactionOrder order, SebPendingTransactionRow row) {
    if (row.ourRef() == null) {
      return Optional.of(ReconciliationAuditRecorder.REASON_MISSING_OUR_REF);
    }
    if (row.isin() == null || row.isin().isBlank()) {
      return Optional.of(ReconciliationAuditRecorder.REASON_MISSING_ISIN);
    }
    if (!row.isin().equals(order.getInstrumentIsin()) || row.side() != order.getTransactionType()) {
      return Optional.of(ReconciliationAuditRecorder.REASON_ISIN_SIDE_MISMATCH);
    }
    return fundResolver
        .resolve(row.clientName())
        .filter((TulevaFund rowFund) -> rowFund != order.getFund())
        .map(rowFund -> ReconciliationAuditRecorder.REASON_FUND_MISMATCH);
  }

  private boolean isAlreadyLinkedToExecution(SebPendingTransactionRow row) {
    if (row.ourRef() != null
        && executionRepository.findByBrokerTransactionId(row.ourRef()).isPresent()) {
      return true;
    }
    if (row.clientRef() != null) {
      return orderRepository
          .findByOrderUuid(row.clientRef())
          .map(order -> !executionRepository.findAllByOrderId(order.getId()).isEmpty())
          .orElse(false);
    }
    return false;
  }
}
