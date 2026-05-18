package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.notification.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class QuantityAmountMismatchAlertListener {

  private final AlertMandrillMessageFactory messageFactory;
  private final EmailService emailService;

  @EventListener
  public void onQuantityAmountMismatch(QuantityAmountMismatchEvent event) {
    var subject = "[HOIATUS] Tehingute koguse/summa lahknevused – " + event.reportDate();
    var body = buildBody(event);

    boolean sent = emailService.sendSystemEmail(messageFactory.create(subject, body));
    if (sent) {
      log.info(
          "Sent quantity/amount mismatch alert: reportDate={}, isin={}, nearMissOrderId={},"
              + " kind={}",
          event.reportDate(),
          event.row().isin(),
          event.nearMissOrder().getId(),
          event.kind());
    } else {
      log.error(
          "Failed to send quantity/amount mismatch alert: reportDate={}, isin={},"
              + " nearMissOrderId={}, kind={}",
          event.reportDate(),
          event.row().isin(),
          event.nearMissOrder().getId(),
          event.kind());
    }
  }

  private static String buildBody(QuantityAmountMismatchEvent event) {
    var row = event.row();
    return """
        SEB pending transactions raportis on rida, mille kogus/summa on
        lubatud lähedusest väljas, kuid ühe konkreetse tellimusega (near-miss)
        siiski lähedal. Tellimus jäi automaatselt sidumata; vajab käsitsi uurimist.

        Tolerance kind: %s

        Raporti kuupäev: %s
        Near-miss order id: %s

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
            event.nearMissOrder().getId(),
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
