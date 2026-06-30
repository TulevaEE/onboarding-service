package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
class ExecutionPriceConsistencyAlertListener {

  private final AlertMandrillMessageFactory messageFactory;
  private final EmailService emailService;
  private final OperationsNotificationService notificationService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onExecutionPriceConsistency(ExecutionPriceConsistencyEvent event) {
    try {
      notificationService.sendMessage(buildSlackMessage(event), INVESTMENT);
    } catch (RuntimeException e) {
      log.error(
          "Failed to send execution price consistency Slack alert: reportDate={}, orderId={}",
          event.reportDate(),
          event.orderId(),
          e);
    }

    var subject = "[HOIATUS] Tehingu tükkide hinnad lahknevad – " + event.reportDate();
    var body = buildBody(event);

    boolean sent = emailService.sendSystemEmail(messageFactory.create(subject, body));
    if (sent) {
      log.info(
          "Sent execution price consistency alert: reportDate={}, isin={}, orderId={}",
          event.reportDate(),
          event.isin(),
          event.orderId());
    } else {
      log.error(
          "Failed to send execution price consistency alert: reportDate={}, isin={}, orderId={}",
          event.reportDate(),
          event.isin(),
          event.orderId());
    }
  }

  private static String buildSlackMessage(ExecutionPriceConsistencyEvent event) {
    return """
        ⚠️ SEB tehingu tükkide hinnad lahknevad – %s
        Order %s, ISIN: %s
        Min hind: %s, max hind: %s, hajuvus: %s (lubatud %s)"""
        .formatted(
            event.reportDate(),
            event.orderId(),
            nullSafe(event.isin()),
            nullSafe(event.minUnitPrice()),
            nullSafe(event.maxUnitPrice()),
            nullSafe(event.relativeSpread()),
            nullSafe(event.tolerance()));
  }

  private static String buildBody(ExecutionPriceConsistencyEvent event) {
    return """
        Sama tellimuse (order UUID) osatäitmiste ühikuhinnad erinevad rohkem kui lubatud lähedus.
        See võib viidata valesti määratud Client ref-ile või eraldi tehingule sama UUID all.
        Vajab käsitsi uurimist.

        Raporti kuupäev: %s
        Order id: %s
        ISIN: %s

        Min ühikuhind: %s
        Max ühikuhind: %s
        Suhteline hajuvus: %s
        Lubatud lähedus: %s
        """
        .formatted(
            event.reportDate(),
            event.orderId(),
            nullSafe(event.isin()),
            nullSafe(event.minUnitPrice()),
            nullSafe(event.maxUnitPrice()),
            nullSafe(event.relativeSpread()),
            nullSafe(event.tolerance()));
  }

  private static String nullSafe(Object value) {
    return value == null ? "(missing)" : value.toString();
  }
}
