package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.report.InvestmentReport;
import ee.tuleva.onboarding.investment.transaction.OrderStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SebPendingTransactionReconciliationService {

  private final SebPendingTransactionExtractor extractor;
  private final SebPendingTransactionMatcher matcher;
  private final SebPendingTransactionComplexMatcher complexMatcher;
  private final TransactionExecutionMapper executionMapper;
  private final TransactionExecutionRepository executionRepository;
  private final TransactionOrderRepository orderRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public void reconcile(InvestmentReport report) {
    var rows = extractor.extract(report);
    log.info(
        "Reconciling SEB pending transactions: reportDate={}, rowCount={}",
        report.getReportDate(),
        rows.size());

    int matched = 0;
    int unmatched = 0;
    for (SebPendingTransactionRow row : rows) {
      Optional<TransactionOrder> orderOpt = matcher.match(row);
      if (orderOpt.isEmpty()) {
        orderOpt = complexMatcher.match(row);
      }
      if (orderOpt.isEmpty()) {
        Optional<QuantityAmountMismatchEvent> nearMiss = complexMatcher.findNearMiss(row);
        if (nearMiss.isPresent()) {
          QuantityAmountMismatchEvent event = nearMiss.get().withReportDate(report.getReportDate());
          log.info(
              "Quantity/amount near-miss: clientRef={}, ourRef={}, isin={}, kind={}, expected={},"
                  + " actual={}, reportDate={}",
              row.clientRef(),
              row.ourRef(),
              row.isin(),
              event.kind(),
              event.expected(),
              event.actual(),
              report.getReportDate());
          eventPublisher.publishEvent(event);
          unmatched++;
          continue;
        }
        // Unmatched rows are surfaced read-only by the daily SettlementCheckJob digest,
        // not alerted per-row here.
        log.info(
            "Unmatched pending transaction: clientRef={}, ourRef={}, isin={}, reportDate={}",
            row.clientRef(),
            row.ourRef(),
            row.isin(),
            report.getReportDate());
        unmatched++;
        continue;
      }
      if (upsert(row, orderOpt.get())) {
        matched++;
      }
    }

    log.info(
        "Reconciliation completed: reportDate={}, matched={}, unmatched={}",
        report.getReportDate(),
        matched,
        unmatched);
  }

  private boolean upsert(SebPendingTransactionRow row, TransactionOrder order) {
    if (wouldOrphanExistingExecution(row, order)) {
      return false;
    }
    Optional<TransactionExecution> existing = executionRepository.findByOrderId(order.getId());
    TransactionExecution execution =
        existing
            .map(
                e -> {
                  executionMapper.applyTo(e, row, order);
                  return e;
                })
            .orElseGet(() -> executionMapper.toExecution(row, order));
    executionRepository.save(execution);

    order.setOrderStatus(OrderStatus.EXECUTED);
    orderRepository.save(order);
    return true;
  }

  private boolean wouldOrphanExistingExecution(
      SebPendingTransactionRow row, TransactionOrder order) {
    if (row.ourRef() == null) {
      return false;
    }
    Optional<TransactionExecution> byBrokerId =
        executionRepository.findByBrokerTransactionId(row.ourRef());
    if (byBrokerId.isEmpty()) {
      return false;
    }
    Long existingOrderId = byBrokerId.get().getOrderId();
    if (existingOrderId == null || existingOrderId.equals(order.getId())) {
      return false;
    }
    log.warn(
        "Refusing to re-link execution to different order: brokerTransactionId={},"
            + " existingOrderId={}, proposedOrderId={}, clientRef={}",
        row.ourRef(),
        existingOrderId,
        order.getId(),
        row.clientRef());
    return true;
  }
}
