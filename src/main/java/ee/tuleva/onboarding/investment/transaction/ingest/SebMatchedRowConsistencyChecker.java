package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.ReconciliationAuditRecorder.REASON_FUND_MISMATCH;
import static ee.tuleva.onboarding.investment.transaction.ingest.ReconciliationAuditRecorder.REASON_ISIN_SIDE_MISMATCH;
import static ee.tuleva.onboarding.investment.transaction.ingest.ReconciliationAuditRecorder.REASON_MISSING_ISIN;
import static ee.tuleva.onboarding.investment.transaction.ingest.ReconciliationAuditRecorder.REASON_MISSING_OUR_REF;

import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SebMatchedRowConsistencyChecker {

  private final SebClientNameToFundResolver fundResolver;
  private final ReconciliationAuditRecorder auditRecorder;

  boolean isConsistent(TransactionOrder order, SebPendingTransactionRow row, LocalDate reportDate) {
    if (row.ourRef() == null) {
      log.error(
          "Matched SEB row has no Our ref, cannot record as a piece: orderId={}, clientRef={},"
              + " isin={}, reportDate={}",
          order.getId(),
          row.clientRef(),
          row.isin(),
          reportDate);
      return rejectInconsistentRow(order, row, REASON_MISSING_OUR_REF, reportDate);
    }
    if (row.isin() == null || row.isin().isBlank()) {
      log.error(
          "Matched SEB row is missing ISIN, cannot verify instrument: orderId={}, clientRef={},"
              + " ourRef={}, reportDate={}",
          order.getId(),
          row.clientRef(),
          row.ourRef(),
          reportDate);
      return rejectInconsistentRow(order, row, REASON_MISSING_ISIN, reportDate);
    }
    if (!row.isin().equals(order.getInstrumentIsin()) || row.side() != order.getTransactionType()) {
      log.error(
          "Matched SEB row instrument/side does not match order: orderId={}, orderIsin={},"
              + " rowIsin={}, orderSide={}, rowSide={}, ourRef={}, reportDate={}",
          order.getId(),
          order.getInstrumentIsin(),
          row.isin(),
          order.getTransactionType(),
          row.side(),
          row.ourRef(),
          reportDate);
      return rejectInconsistentRow(order, row, REASON_ISIN_SIDE_MISMATCH, reportDate);
    }
    Optional<TulevaFund> rowFund = fundResolver.resolve(row.clientName());
    if (rowFund.isPresent() && rowFund.get() != order.getFund()) {
      log.error(
          "Matched SEB row client name resolves to a different fund than the order: orderId={},"
              + " orderFund={}, rowFund={}, clientName={}, ourRef={}, reportDate={}",
          order.getId(),
          order.getFund(),
          rowFund.get(),
          row.clientName(),
          row.ourRef(),
          reportDate);
      return rejectInconsistentRow(order, row, REASON_FUND_MISMATCH, reportDate);
    }
    return true;
  }

  private boolean rejectInconsistentRow(
      TransactionOrder order, SebPendingTransactionRow row, String reason, LocalDate reportDate) {
    auditRecorder.recordInconsistentMatchedRow(order, row, reason, reportDate);
    return false;
  }
}
