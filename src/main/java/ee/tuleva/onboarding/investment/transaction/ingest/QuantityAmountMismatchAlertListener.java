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
class QuantityAmountMismatchAlertListener {

  private final AlertMandrillMessageFactory messageFactory;
  private final EmailService emailService;
  private final OperationsNotificationService notificationService;

  // Fire after the reconcile transaction commits so a Slack/email failure can never roll back
  // persisted reconciliation state; fallbackExecution lets it still run outside a transaction.
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onQuantityAmountMismatch(QuantityAmountMismatchEvent event) {
    try {
      notificationService.sendMessage(buildSlackMessage(event), INVESTMENT);
    } catch (RuntimeException e) {
      log.error(
          "Failed to send quantity/amount mismatch Slack alert: reportDate={}, orderId={}",
          event.reportDate(),
          event.order().getId(),
          e);
    }

    var subject = "[HOIATUS] Tehingute koguse/summa lahknevused – " + event.reportDate();
    var body = buildBody(event);

    boolean sent = emailService.sendSystemEmail(messageFactory.create(subject, body));
    if (sent) {
      log.info(
          "Sent quantity/amount mismatch alert: reportDate={}, isin={}, orderId={}, kind={}",
          event.reportDate(),
          event.row().isin(),
          event.order().getId(),
          event.kind());
    } else {
      log.error(
          "Failed to send quantity/amount mismatch alert: reportDate={}, isin={}, orderId={},"
              + " kind={}",
          event.reportDate(),
          event.row().isin(),
          event.order().getId(),
          event.kind());
    }
  }

  private static String buildSlackMessage(QuantityAmountMismatchEvent event) {
    return """
        ⚠️ SEB tehingu koguse/summa lahknevus – %s
        Order %s, ISIN: %s, liik: %s
        Expected: %s, actual: %s, delta: %s"""
        .formatted(
            event.reportDate(),
            event.order().getId(),
            nullSafe(event.row().isin()),
            event.kind(),
            nullSafe(event.expected()),
            nullSafe(event.actual()),
            nullSafe(event.delta()));
  }

  private static String buildBody(QuantityAmountMismatchEvent event) {
    var row = event.row();
    return """
        SEB pending transactions raportis on rida, mille kogus/summa erineb
        tellimuse omast rohkem kui lubatud lähedus. Vajab käsitsi uurimist.

        Tolerance kind: %s

        Raporti kuupäev: %s
        Order id: %s

        Expected: %s
        Actual: %s
        Delta: %s

        ISIN: %s
        Client ref: %s
        Our ref: %s
        Side: %s
        Trade date: %s
        Settlement date: %s
        Client name: %s
        Account: %s
        Instrument name: %s
        """
        .formatted(
            event.kind(),
            event.reportDate(),
            event.order().getId(),
            nullSafe(event.expected()),
            nullSafe(event.actual()),
            nullSafe(event.delta()),
            nullSafe(row.isin()),
            row.clientRef() == null ? "(missing)" : row.clientRef().toString(),
            nullSafe(row.ourRef()),
            nullSafe(row.side()),
            nullSafe(row.tradeDate()),
            nullSafe(row.settlementDate()),
            nullSafe(row.clientName()),
            nullSafe(row.account()),
            nullSafe(row.instrumentName()));
  }

  private static String nullSafe(Object value) {
    return value == null ? "(missing)" : value.toString();
  }
}
