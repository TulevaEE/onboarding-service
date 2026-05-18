package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.notification.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class UnmatchedExecutionAlertListener {

  private final AlertMandrillMessageFactory messageFactory;
  private final EmailService emailService;

  @EventListener
  public void onUnmatchedPendingTransaction(UnmatchedPendingTransactionEvent event) {
    var row = event.row();
    var subject = "[HOIATUS] Matchimata tehing SEB raportis – " + event.reportDate();
    var body = buildBody(event, row);

    boolean sent = emailService.sendSystemEmail(messageFactory.create(subject, body));
    if (sent) {
      log.info(
          "Sent unmatched SEB transaction alert: reportDate={}, isin={}, ourRef={}",
          event.reportDate(),
          row.isin(),
          row.ourRef());
    } else {
      log.error(
          "Failed to send unmatched SEB transaction alert: reportDate={}, isin={}, ourRef={}",
          event.reportDate(),
          row.isin(),
          row.ourRef());
    }
  }

  private static String buildBody(
      UnmatchedPendingTransactionEvent event, SebPendingTransactionRow row) {
    return """
        SEB pending transactions raportis on rida, mida ei õnnestunud ühegi tellimusega kokku viia.

        Raporti kuupäev: %s

        ISIN: %s
        Client ref: %s
        Our ref: %s
        Quantity: %s
        Side: %s
        Trade date: %s
        Settlement date: %s
        Client name: %s
        Account: %s
        Instrument name: %s

        Tehing ootab käsitsi uurimist.
        """
        .formatted(
            event.reportDate(),
            nullSafe(row.isin()),
            row.clientRef() == null ? "(missing)" : row.clientRef().toString(),
            nullSafe(row.ourRef()),
            nullSafe(row.quantity()),
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
