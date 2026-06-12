package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.util.List;
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

  List<SebPendingTransactionRow> collectUnmatched(InvestmentReport report) {
    TransactionMatchingProperties matchingProperties = matchingPolicy.current();
    return extractor.extract(report).stream()
        .filter(row -> !isAlreadyLinkedToExecution(row))
        .filter(row -> matcher.match(row).isEmpty())
        .filter(row -> complexMatcher.match(row, matchingProperties).isEmpty())
        .filter(row -> !complexMatcher.hasNearMissCandidate(row, matchingProperties))
        .toList();
  }

  private boolean isAlreadyLinkedToExecution(SebPendingTransactionRow row) {
    if (row.ourRef() != null
        && executionRepository.findByBrokerTransactionId(row.ourRef()).isPresent()) {
      return true;
    }
    if (row.clientRef() != null) {
      return orderRepository
          .findByOrderUuid(row.clientRef())
          .flatMap(order -> executionRepository.findByOrderId(order.getId()))
          .isPresent();
    }
    return false;
  }
}
