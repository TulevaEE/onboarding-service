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
class PossibleReportTruncationAlertListener {

  private final AlertMandrillMessageFactory messageFactory;
  private final EmailService emailService;
  private final OperationsNotificationService notificationService;

  // Fire after the reconcile transaction commits so a Slack/email failure can never roll back
  // persisted reconciliation state; fallbackExecution lets it still run outside a transaction.
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onPossibleReportTruncation(PossibleReportTruncationEvent event) {
    try {
      notificationService.sendMessage(buildSlackMessage(event), INVESTMENT);
    } catch (RuntimeException e) {
      log.error(
          "Failed to send possible report truncation Slack alert: reportDate={}",
          event.reportDate(),
          e);
    }

    var subject =
        "[HOIATUS] SEB pending transactions raport võib olla poolik – " + event.reportDate();
    var body = buildBody(event);

    boolean sent = emailService.sendSystemEmail(messageFactory.create(subject, body));
    if (sent) {
      log.info(
          "Sent possible report truncation alert: reportDate={}, rowCount={}, priorReportDate={},"
              + " priorRowCount={}",
          event.reportDate(),
          event.rowCount(),
          event.priorReportDate(),
          event.priorRowCount());
    } else {
      log.error(
          "Failed to send possible report truncation alert: reportDate={}, rowCount={}",
          event.reportDate(),
          event.rowCount());
    }
  }

  private static String buildSlackMessage(PossibleReportTruncationEvent event) {
    return """
        ⚠️ SEB pending transactions raport võib olla poolik – %s
        Ridu: %s (eelmine raport %s: %s rida)
        Kadumise põhjal tuvastamist ei tehtud, oodatakse käsitsi kontrolli."""
        .formatted(
            event.reportDate(), event.rowCount(), event.priorReportDate(), event.priorRowCount());
  }

  private static String buildBody(PossibleReportTruncationEvent event) {
    return """
        SEB pending transactions raporti ridade arv langes eelmise raportiga võrreldes järsult.
        See võib viidata poolikult üleslaetud failile. Kadumise põhjal tehingute sulgemine
        (settlement-by-absence) selle raporti jaoks jäeti vahele, et vältida tellimuste
        valesti sulgemist. Vajab käsitsi uurimist.

        Raporti kuupäev: %s
        Ridu: %s

        Eelmise raporti kuupäev: %s
        Eelmise raporti ridu: %s
        """
        .formatted(
            event.reportDate(), event.rowCount(), event.priorReportDate(), event.priorRowCount());
  }
}
