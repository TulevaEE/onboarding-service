package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.export.CustodianOrderEmailAlerter.NOT_POSTED;
import static ee.tuleva.onboarding.investment.transaction.export.CustodianOrderEmailAlerter.POSTED;
import static ee.tuleva.onboarding.investment.transaction.export.CustodianOrderEmailAlerter.SENDING;
import static ee.tuleva.onboarding.investment.transaction.export.CustodianOrderEmailAlerter.SEND_RESULT_TYPE;
import static ee.tuleva.onboarding.investment.transaction.export.CustodianOrderEmailAlerter.alertData;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT;
import static java.util.stream.Collectors.joining;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.event.EventLog;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import ee.tuleva.onboarding.notification.email.EmailSendResult;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
class CustodianOrderEmailNotifier {

  private final OperationsNotificationService notificationService;
  private final EventLogRepository eventLogRepository;
  private final Clock clock;
  private final TransactionTemplate inItsOwnTransactionSinceTheBatchHasCommitted;

  CustodianOrderEmailNotifier(
      OperationsNotificationService notificationService,
      EventLogRepository eventLogRepository,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.notificationService = notificationService;
    this.eventLogRepository = eventLogRepository;
    this.clock = clock;
    this.inItsOwnTransactionSinceTheBatchHasCommitted = new TransactionTemplate(transactionManager);
    this.inItsOwnTransactionSinceTheBatchHasCommitted.setPropagationBehavior(
        PROPAGATION_REQUIRES_NEW);
  }

  void reportSendAttempt(long batchId, MandrillMessage message) {
    var resultUnknown =
        "🔴 Orders email result UNKNOWN: %s\nNo send result was recorded: the service may have stopped while sending.\nIt may have gone out. Check the Cc copy or ask the desk before sending again."
            .formatted(message.getSubject());
    record(batchId, resultUnknown, ERROR, SENDING);
  }

  void reportSendResult(long batchId, MandrillMessage message, EmailSendResult result) {
    var everyoneAccepted = result.notAccepted().isEmpty();
    var text =
        switch (result.outcome()) {
          case SENT ->
              everyoneAccepted
                  ? "✅ Orders emailed: " + summary(message)
                  : "🔴 Orders emailed, but not to every copy: %s\n%s\nThe orders went out, don't send them again. Find out why Mandrill rejected the copy."
                      .formatted(summary(message), cause(result));
          case FAILED ->
              "🔴 Orders NOT sent: %s\n%s\nNothing went out to the desk. Fix the cause, then send the orders again."
                  .formatted(message.getSubject(), cause(result));
          case UNKNOWN ->
              "🔴 Orders email result UNKNOWN: %s\n%s\nIt may have gone out. Check the Cc copy or ask the desk before sending again."
                  .formatted(message.getSubject(), cause(result));
        };
    post(batchId, text, result.outcome() == SENT && everyoneAccepted ? INFO : ERROR);
  }

  private void post(long batchId, String text, Severity severity) {
    var sendResult = record(batchId, text, severity, NOT_POSTED);
    try {
      notificationService.sendMessage(text, TRADING, severity);
    } catch (RuntimeException e) {
      log.warn(
          "Order email send result not posted to Slack, the scheduled run posts it if it was"
              + " recorded: batchId={}, recorded={}, error={}",
          batchId,
          sendResult != null,
          e.toString());
      return;
    }
    if (sendResult != null) {
      markPosted(sendResult, text, severity);
    }
  }

  private @Nullable EventLog record(long batchId, String text, Severity severity, String alert) {
    try {
      return inItsOwnTransactionSinceTheBatchHasCommitted.execute(
          status ->
              save(
                  eventLogRepository
                      .findByTypeAndPrincipal(SEND_RESULT_TYPE, String.valueOf(batchId))
                      .orElseGet(
                          () ->
                              EventLog.builder()
                                  .type(SEND_RESULT_TYPE)
                                  .principal(String.valueOf(batchId))
                                  .build()),
                  text,
                  severity,
                  alert));
    } catch (RuntimeException e) {
      log.error(
          "Order email send result not recorded: batchId={}, alert={}, text={}",
          batchId,
          alert,
          text,
          e);
      return null;
    }
  }

  private void markPosted(EventLog sendResult, String text, Severity severity) {
    try {
      inItsOwnTransactionSinceTheBatchHasCommitted.executeWithoutResult(
          status -> save(sendResult, text, severity, POSTED));
    } catch (RuntimeException e) {
      log.warn(
          "Order email send result posted to Slack but not marked as posted, the scheduled run"
              + " may post it again: principal={}, error={}",
          sendResult.getPrincipal(),
          e.toString());
    }
  }

  private EventLog save(EventLog sendResult, String text, Severity severity, String alert) {
    sendResult.setTimestamp(clock.instant());
    sendResult.setData(alertData(text, severity, alert));
    eventLogRepository.save(sendResult);
    return sendResult;
  }

  private static String summary(MandrillMessage message) {
    return "%s, from %s, %d files"
        .formatted(message.getSubject(), message.getFromEmail(), message.getAttachments().size());
  }

  private static String cause(EmailSendResult result) {
    var notAccepted = result.notAccepted();
    if (notAccepted.isEmpty()) {
      return String.valueOf(result.problem());
    }
    return "Not accepted: "
        + notAccepted.stream().map(CustodianOrderEmailNotifier::describe).collect(joining(", "));
  }

  private static String describe(MandrillMessageStatus status) {
    var refusal = status.getEmail() + " " + status.getStatus();
    return status.getRejectReason() == null
        ? refusal
        : refusal + " (" + status.getRejectReason() + ")";
  }
}
