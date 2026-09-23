package ee.tuleva.onboarding.banking.payment;

import ee.tuleva.onboarding.banking.payment.PaymentStatusReport.TransactionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStatusReportHandler {
  private final PaymentStatusReportExtractor extractor;
  private final OutgoingPaymentService outgoingPaymentService;
  private final ApplicationEventPublisher eventPublisher;

  public void handle(String xml) {
    var report = extractor.extract(xml);

    if (report.isFileLevelOnly()) {
      log.warn(
          "Payment status report carries no per-transaction statuses, only a file-level one: groupStatus={}",
          report.groupStatus());
      return;
    }

    for (var transaction : report.transactionStatuses()) {
      switch (transaction.status()) {
        case REJECTED -> recordRejected(transaction);
        case CANCELLED -> recordCancelled(transaction);
        default ->
            log.info(
                "Payment status report: endToEndId={}, status={}",
                transaction.endToEndId(),
                transaction.status());
      }
    }
  }

  private void recordRejected(TransactionStatus transaction) {
    log.error(
        "Bank rejected a payment we submitted: endToEndId={}, reasonCode={}",
        transaction.endToEndId(),
        transaction.reasonCode());
    outgoingPaymentService.recordFailed(
        transaction.endToEndId(), "Rejected by the bank: " + transaction.reasonCode());
    eventPublisher.publishEvent(
        new PaymentRejectedEvent(transaction.endToEndId(), transaction.reasonCode()));
  }

  private void recordCancelled(TransactionStatus transaction) {
    log.warn(
        "Payment cancelled at the bank before it was paid: endToEndId={}, reasonCode={}",
        transaction.endToEndId(),
        transaction.reasonCode());
    outgoingPaymentService.recordFailed(
        transaction.endToEndId(), "Cancelled at the bank: reasonCode=" + transaction.reasonCode());
  }
}
