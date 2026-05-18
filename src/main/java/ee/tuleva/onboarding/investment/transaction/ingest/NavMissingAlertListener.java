package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.notification.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class NavMissingAlertListener {

  private final AlertMandrillMessageFactory messageFactory;
  private final EmailService emailService;

  @EventListener
  public void onNavMissing(NavMissingEvent event) {
    var subject = "[INFO] NAV andmed puuduvad – " + event.tradeDate();
    var body = buildBody(event);

    boolean sent = emailService.sendSystemEmail(messageFactory.create(subject, body));
    if (sent) {
      log.info(
          "Sent NAV missing alert: executionId={}, isin={}, tradeDate={}",
          event.executionId(),
          event.isin(),
          event.tradeDate());
    } else {
      log.error(
          "Failed to send NAV missing alert: executionId={}, isin={}",
          event.executionId(),
          event.isin());
    }
  }

  private static String buildBody(NavMissingEvent event) {
    return """
        SEB tehingu jaoks ei õnnestunud leida sisemist NAV-hinda.
        See on informatiivne teade — andmelünk on ootuspärane väikese hulga tehingute puhul.

        Execution id: %s
        ISIN: %s
        Trade date: %s
        """
        .formatted(event.executionId(), event.isin(), event.tradeDate());
  }
}
