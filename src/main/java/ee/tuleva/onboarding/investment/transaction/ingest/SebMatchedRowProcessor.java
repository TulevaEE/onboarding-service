package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlement;
import ee.tuleva.onboarding.investment.transaction.TransactionSettlementRepository;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SebMatchedRowProcessor {

  private final SebMatchedRowConsistencyChecker consistencyChecker;
  private final QuantityAmountValidator quantityAmountValidator;
  private final ExecutionPriceConsistencyChecker priceConsistencyChecker;
  private final SebExecutionUpserter executionUpserter;
  private final TransactionExecutionRepository executionRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ReconciliationAuditRecorder auditRecorder;
  private final TransactionSettlementRepository settlementRepository;

  RowOutcome process(
      TransactionOrder order,
      SebPendingTransactionRow row,
      LocalDate reportDate,
      LocalDate asOfDate,
      TransactionMatchingProperties matchingProperties) {
    if (!consistencyChecker.isConsistent(order, row, reportDate)) {
      return RowOutcome.MATCHED;
    }
    Optional<TransactionSettlement> settlement = settlementRepository.findByOrderId(order.getId());
    if (settlement.isPresent()) {
      handleSettledOrderReappearance(order, settlement.get(), row, reportDate);
      return RowOutcome.MATCHED;
    }
    Optional<QuantityAmountMismatchEvent> blankEconomics =
        quantityAmountValidator.detectBlankEconomics(order, row, matchingProperties);
    if (blankEconomics.isPresent()) {
      reportMismatch(blankEconomics.get().withReportDate(reportDate), row);
      return RowOutcome.MATCHED;
    }
    Optional<QuantityAmountMismatchEvent> mismatch =
        quantityAmountValidator.validateCumulative(
            order, row, executionRepository.findAllByOrderId(order.getId()), matchingProperties);
    if (mismatch.isPresent()) {
      reportMismatch(mismatch.get().withReportDate(reportDate), row);
      return RowOutcome.MATCHED;
    }
    if (executionUpserter.upsert(row, order, reportDate, asOfDate)) {
      checkPriceConsistency(order, reportDate, matchingProperties);
      return RowOutcome.MATCHED;
    }
    return RowOutcome.SKIPPED;
  }

  private void checkPriceConsistency(
      TransactionOrder order, LocalDate reportDate, TransactionMatchingProperties properties) {
    priceConsistencyChecker
        .check(
            order,
            executionRepository.findAllByOrderId(order.getId()),
            properties.executionPriceConsistencyTolerance())
        .ifPresent(
            event -> {
              log.error(
                  "Cross-piece price divergence: orderId={}, isin={}, min={}, max={}, spread={},"
                      + " tolerance={}, reportDate={}",
                  order.getId(),
                  order.getInstrumentIsin(),
                  event.minUnitPrice(),
                  event.maxUnitPrice(),
                  event.relativeSpread(),
                  event.tolerance(),
                  reportDate);
              eventPublisher.publishEvent(event.withReportDate(reportDate));
            });
  }

  private void reportMismatch(QuantityAmountMismatchEvent event, SebPendingTransactionRow row) {
    log.info(
        "Quantity/amount mismatch: clientRef={}, ourRef={}, isin={}, kind={}, expected={},"
            + " actual={}, reportDate={}",
        row.clientRef(),
        row.ourRef(),
        row.isin(),
        event.kind(),
        event.expected(),
        event.actual(),
        event.reportDate());
    auditRecorder.recordQuantityAmountMismatch(event);
    eventPublisher.publishEvent(event);
  }

  private void handleSettledOrderReappearance(
      TransactionOrder order,
      TransactionSettlement settlement,
      SebPendingTransactionRow row,
      LocalDate reportDate) {
    if (!reportDate.isAfter(settlement.getReportDate())) {
      return;
    }
    log.error(
        "Settled order reappeared in pending report: orderId={}, settlementReportDate={},"
            + " reportDate={}, clientRef={}, ourRef={}",
        order.getId(),
        settlement.getReportDate(),
        reportDate,
        row.clientRef(),
        row.ourRef());
    auditRecorder.recordSettlementReappeared(order, settlement, row, reportDate);
  }
}
