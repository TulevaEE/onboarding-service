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

  void reportSendAttempt(String emailKey, MandrillMessage message) {
    alertRows.recordSendAttempt(
        emailKey,
        resultUnknown(
            message.getSubject(),
            "No send result was recorded: the service may have stopped while sending."));
  }

  void reportSendResult(String emailKey, MandrillMessage message, EmailSendResult result) {
    var everyoneAccepted = result.notAccepted().isEmpty();
    var text =
        switch (result.outcome()) {
          case SENT ->
              everyoneAccepted
                  ? "✅ Orders emailed: " + summary(message)
                  : "🔴 Orders emailed, but not to every copy: %s\n%s\nThe orders went out, don't send them again. Find out why Mandrill rejected the copy."
                      .formatted(summary(message), cause(result));
          case FAILED -> ordersNotSent(message.getSubject(), cause(result));
          case UNKNOWN -> resultUnknown(message.getSubject(), cause(result));
        };
    post(emailKey, text, result.outcome() == SENT && everyoneAccepted ? INFO : ERROR);
  }

  void recordNotBuilt(String emailKey, String subject, String problem) {
    alertRows.recordFailedSend(emailKey, notBuilt(subject, problem));
  }

  void postNotBuilt(String emailKey, String subject, String problem) {
    var text = notBuilt(subject, problem);
    if (postedToSlack(emailKey, text, ERROR)) {
      markPosted(emailKey, text);
    }
  }

  private static String notBuilt(String subject, String problem) {
    return ordersNotSent(subject, "The message could not be built: " + problem);
  }

  private static String resultUnknown(String subject, String cause) {
    return "🔴 Orders email result UNKNOWN: %s\n%s\nIt may have gone out. Check the Cc copy or ask the desk before sending again."
        .formatted(subject, cause);
  }

  private static String ordersNotSent(String subject, String cause) {
    return "🔴 Orders NOT sent: %s\n%s\nNothing went out to the desk. Fix the cause, then send the orders again."
        .formatted(subject, cause);
  }

  private void post(String emailKey, String text, Severity severity) {
    var recorded = written(emailKey, () -> alertRows.recordSendResult(emailKey, text, severity));
    var posted = postedToSlack(emailKey, text, severity);
    if (recorded) {
      if (posted) {
        markPosted(emailKey, text);
      }
      return;
    }
    var writtenOnRetry =
        written(
            emailKey,
            () -> {
              if (posted) {
                alertRows.recordPostedSendResult(emailKey, text, severity);
              } else {
                alertRows.recordSendResult(emailKey, text, severity);
              }
            });
    if (!writtenOnRetry) {
      log.error(
          "Order email send result not recorded on retry either, so the scheduled run may post an"
              + " UNKNOWN for this email: emailKey={}, postedToSlack={}, text={}",
          emailKey,
          posted,
          text);
    }
  }

  private boolean written(String emailKey, Runnable write) {
    try {
      write.run();
      return true;
    } catch (RuntimeException e) {
      log.warn(
          "Order email send result not recorded: emailKey={}, error={}", emailKey, e.toString());
      return false;
    }
  }

  private boolean postedToSlack(String emailKey, String text, Severity severity) {
    try {
      notificationService.sendMessage(text, TRADING, severity);
      return true;
    } catch (RuntimeException e) {
      log.warn(
          "Order email send result not posted to Slack, the scheduled run posts it if it was"
              + " recorded: emailKey={}, error={}",
          emailKey,
          e.toString());
      return false;
    }
  }

  private void markPosted(String emailKey, String text) {
    try {
      alertRows.markSendResultPosted(emailKey, text);
    } catch (RuntimeException e) {
      log.warn(
          "Order email send result posted to Slack but not marked as posted, the scheduled run"
              + " may post it again: emailKey={}, error={}",
          emailKey,
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
