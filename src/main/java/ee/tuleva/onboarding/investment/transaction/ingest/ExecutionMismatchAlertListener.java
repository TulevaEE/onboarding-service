package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class ExecutionMismatchAlertListener {

  private final AlertMandrillMessageFactory messageFactory;
  private final EmailService emailService;
  private final OperationsNotificationService notificationService;

  @EventListener
  public void onExecutionMismatch(ExecutionMismatchEvent event) {
    notificationService.sendMessage(buildSlackMessage(event), INVESTMENT);

    var subject = "[HOIATUS] SEB hind erineb NAV-hinnast – " + event.tradeDate();
    var body = buildBody(event);

    boolean sent = emailService.sendSystemEmail(messageFactory.create(subject, body));
    if (sent) {
      log.info(
          "Sent execution mismatch alert: executionId={}, isin={}, deltaPercent={}",
          event.executionId(),
          event.isin(),
          event.deltaPercent());
    } else {
      log.error(
          "Failed to send execution mismatch alert: executionId={}, isin={}",
          event.executionId(),
          event.isin());
    }
  }

  private static String buildSlackMessage(ExecutionMismatchEvent event) {
    return """
        ⚠️ SEB hind erineb NAV-hinnast – %s
        Execution %s, ISIN: %s
        Exec hind: %s, NAV hind: %s, delta %%: %s"""
        .formatted(
            event.tradeDate(),
            event.executionId(),
            event.isin(),
            event.execPrice(),
            event.navPrice(),
            event.deltaPercent());
  }

  private static String buildBody(ExecutionMismatchEvent event) {
    return """
        SEB tehinguhind erineb sisemisest NAV-hinnast üle lubatud piiri.

        Execution id: %s
        ISIN: %s
        Exec price: %s
        NAV price: %s
        Delta %%: %s
        Trade date: %s

        Tehing ootab käsitsi uurimist.
        """
        .formatted(
            event.executionId(),
            event.isin(),
            event.execPrice(),
            event.navPrice(),
            event.deltaPercent(),
            event.tradeDate());
  }
}
