package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.export.OrderEmailSlackText.noResultRecorded;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmailSlackText.notBuilt;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmailSlackText.notSent;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmailSlackText.problemSeverity;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmailSlackText.resultUnknown;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmailSlackText.sent;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmailSlackText.sentButNotToEveryCopy;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.TRADING;
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

  void reportSendAttempt(OrderEmailSummary summary) {
    alertRows.recordSendAttempt(
        summary.key(), noResultRecorded(summary), problemSeverity(summary.email()));
  }

  void reportSendResult(
      OrderEmailSummary summary, MandrillMessage message, EmailSendResult result) {
    var everyoneAccepted = result.notAccepted().isEmpty();
    var text =
        switch (result.outcome()) {
          case SENT ->
              everyoneAccepted
                  ? sent(summary, message)
                  : sentButNotToEveryCopy(summary, cause(result));
          case FAILED -> notSent(summary, cause(result));
          case UNKNOWN -> resultUnknown(summary, cause(result));
        };
    post(
        summary.key(),
        text,
        result.outcome() == SENT && everyoneAccepted ? INFO : problemSeverity(summary.email()));
  }

  void recordNotBuilt(OrderEmailSummary summary, String problem) {
    alertRows.recordFailedSend(
        summary.key(), notBuilt(summary, problem), problemSeverity(summary.email()));
  }

  void postNotBuilt(OrderEmailSummary summary, String problem) {
    var text = notBuilt(summary, problem);
    if (postedToSlack(summary.key(), text, problemSeverity(summary.email()))) {
      markPosted(summary.key(), text);
    }
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
