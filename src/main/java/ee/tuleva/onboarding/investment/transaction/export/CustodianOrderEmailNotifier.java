package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT;
import static java.util.stream.Collectors.joining;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import ee.tuleva.onboarding.notification.email.EmailSendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class CustodianOrderEmailNotifier {

  private final OperationsNotificationService notificationService;
  private final OrderEmailAlertRows alertRows;

  void reportSendAttempt(long batchId, MandrillMessage message) {
    alertRows.recordSendAttempt(
        batchId,
        "🔴 Orders email result UNKNOWN: %s\nNo send result was recorded: the service may have stopped while sending.\nIt may have gone out. Check the Cc copy or ask the desk before sending again."
            .formatted(message.getSubject()));
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
          case FAILED -> ordersNotSent(message.getSubject(), cause(result));
          case UNKNOWN ->
              "🔴 Orders email result UNKNOWN: %s\n%s\nIt may have gone out. Check the Cc copy or ask the desk before sending again."
                  .formatted(message.getSubject(), cause(result));
        };
    post(batchId, text, result.outcome() == SENT && everyoneAccepted ? INFO : ERROR);
  }

  void reportNotBuilt(long batchId, String subject, String problem) {
    post(batchId, ordersNotSent(subject, "The message could not be built: " + problem), ERROR);
  }

  private static String ordersNotSent(String subject, String cause) {
    return "🔴 Orders NOT sent: %s\n%s\nNothing went out to the desk. Fix the cause, then send the orders again."
        .formatted(subject, cause);
  }

  private void post(long batchId, String text, Severity severity) {
    var recorded = written(batchId, () -> alertRows.recordSendResult(batchId, text, severity));
    var posted = postedToSlack(batchId, text, severity);
    if (recorded) {
      if (posted) {
        markPosted(batchId, text);
      }
      return;
    }
    var writtenOnRetry =
        written(
            batchId,
            () -> {
              if (posted) {
                alertRows.recordPostedSendResult(batchId, text, severity);
              } else {
                alertRows.recordSendResult(batchId, text, severity);
              }
            });
    if (!writtenOnRetry) {
      log.error(
          "Order email send result not recorded on retry either, so the scheduled run may post an"
              + " UNKNOWN for this batch: batchId={}, postedToSlack={}, text={}",
          batchId,
          posted,
          text);
    }
  }

  private boolean written(long batchId, Runnable write) {
    try {
      write.run();
      return true;
    } catch (RuntimeException e) {
      log.warn("Order email send result not recorded: batchId={}, error={}", batchId, e.toString());
      return false;
    }
  }

  private boolean postedToSlack(long batchId, String text, Severity severity) {
    try {
      notificationService.sendMessage(text, TRADING, severity);
      return true;
    } catch (RuntimeException e) {
      log.warn(
          "Order email send result not posted to Slack, the scheduled run posts it if it was"
              + " recorded: batchId={}, error={}",
          batchId,
          e.toString());
      return false;
    }
  }

  private void markPosted(long batchId, String text) {
    try {
      alertRows.markSendResultPosted(batchId, text);
    } catch (RuntimeException e) {
      log.warn(
          "Order email send result posted to Slack but not marked as posted, the scheduled run"
              + " may post it again: batchId={}, error={}",
          batchId,
          e.toString());
    }
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
