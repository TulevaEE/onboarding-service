package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.export.CustodianOrderEmailFailureAlerter.SEND_RESULT_TYPE;
import static ee.tuleva.onboarding.investment.transaction.export.CustodianOrderEmailFailureAlerter.notYetPosted;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.SENT;
import static java.util.stream.Collectors.joining;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.event.EventLogRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import ee.tuleva.onboarding.notification.email.EmailSendResult;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
class CustodianOrderEmailNotifier {

  private final EventLogRepository eventLogRepository;
  private final PlatformTransactionManager transactionManager;
  private final Clock clock;

  void reportSendResult(MandrillMessage message, EmailSendResult result) {
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
    record(message, text, result.outcome() == SENT && everyoneAccepted ? INFO : ERROR);
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

  private void record(MandrillMessage message, String text, Severity severity) {
    try {
      inATransactionOfItsOwnSinceTheBatchHasCommitted()
          .executeWithoutResult(
              status ->
                  eventLogRepository.save(
                      notYetPosted(
                          SEND_RESULT_TYPE,
                          message.getSubject(),
                          clock.instant(),
                          text,
                          severity)));
    } catch (RuntimeException e) {
      log.error("Order email result not recorded for the trading channel: text={}", text, e);
    }
  }

  private TransactionTemplate inATransactionOfItsOwnSinceTheBatchHasCommitted() {
    var transaction = new TransactionTemplate(transactionManager);
    transaction.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
    return transaction;
  }
}
