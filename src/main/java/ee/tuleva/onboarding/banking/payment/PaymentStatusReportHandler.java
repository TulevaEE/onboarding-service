package ee.tuleva.onboarding.banking.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Acts on the bank's own verdict, which until now was logged and discarded.
 *
 * <p>A rejection is otherwise invisible to us until statement reconciliation or a client complaint.
 */
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
      // Whether SEB reports per payment or only per file has never been confirmed. Say which one
      // this was, so the answer comes from production rather than from an assumption.
      log.warn(
          "Payment status report carries no per-transaction statuses, only a file-level one: groupStatus={}",
          report.groupStatus());
      return;
    }

    for (var transaction : report.transactionStatuses()) {
      if (transaction.status().isRejection()) {
        log.error(
            "Bank rejected a payment we submitted: endToEndId={}, reasonCode={}",
            transaction.endToEndId(),
            transaction.reasonCode());
        outgoingPaymentService.recordFailed(
            transaction.endToEndId(), "Rejected by the bank: " + transaction.reasonCode());
        eventPublisher.publishEvent(
            new PaymentRejectedEvent(transaction.endToEndId(), transaction.reasonCode()));
      } else {
        log.info(
            "Payment status report: endToEndId={}, status={}",
            transaction.endToEndId(),
            transaction.status());
      }
    }
  }
}
