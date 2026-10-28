package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
class SebFundConfirmationDigest {

  private static final String HEADER = "SEB fonditehingute kinnitused";
  private static final String BLOCK_SEPARATOR = "\n\n";

  private final OperationsNotificationService notificationService;
  private final SebFundConfirmationAuditRecorder auditRecorder;
  private final SebFundConfirmationAlertText text;

  SebFundConfirmationDigest(
      OperationsNotificationService notificationService,
      SebFundConfirmationAuditRecorder auditRecorder,
      SebFundConfirmationProperties properties) {
    this.notificationService = notificationService;
    this.auditRecorder = auditRecorder;
    this.text = new SebFundConfirmationAlertText(properties);
  }

  void publish(
      List<SebFundConfirmationOutcome> outcomes,
      List<MissingSebFundConfirmation> missing,
      List<UnreadableSebFundConfirmation> unreadable,
      List<MisnamedSebFundConfirmation> misnamed,
      List<SebFundConfirmationCheckFailure> failures) {
    List<NewFindings> newFindings =
        outcomes.stream()
            .filter(outcome -> outcome.result().isActionable())
            .map(outcome -> new NewFindings(outcome, auditRecorder.unalertedFindings(outcome)))
            .filter(found -> !found.findings().isEmpty())
            .toList();
    List<MissingSebFundConfirmation> newMissing =
        missing.stream().filter(trade -> !auditRecorder.alreadyAlerted(trade)).toList();
    List<UnreadableSebFundConfirmation> newUnreadable =
        unreadable.stream().filter(file -> !auditRecorder.alreadyAlerted(file)).toList();
    List<MisnamedSebFundConfirmation> newMisnamed =
        misnamed.stream().filter(file -> !auditRecorder.alreadyAlerted(file)).toList();

    List<String> blocks = new ArrayList<>();
    newFindings.forEach(found -> blocks.addAll(text.blocks(found.outcome(), found.findings())));
    newMissing.forEach(trade -> blocks.add(text.block(trade)));
    newUnreadable.forEach(file -> blocks.add(text.block(file)));
    newMisnamed.forEach(file -> blocks.add(text.block(file)));
    List<SebFundConfirmationCheckFailure> newFailures =
        failures.stream().filter(failure -> !auditRecorder.alreadyAlertedToday(failure)).toList();
    newFailures.forEach(failure -> blocks.add(text.block(failure)));
    if (blocks.isEmpty()
        || !send(HEADER + BLOCK_SEPARATOR + String.join(BLOCK_SEPARATOR, blocks))) {
      return;
    }
    try {
      newFindings.forEach(found -> auditRecorder.recordAlerted(found.outcome(), found.findings()));
      newMissing.forEach(auditRecorder::recordAlerted);
      newUnreadable.forEach(auditRecorder::recordAlerted);
      newMisnamed.forEach(auditRecorder::recordAlerted);
      newFailures.forEach(auditRecorder::recordAlerted);
    } catch (RuntimeException e) {
      log.error(
          "SEB fund confirmation alerts sent but not recorded, they may repeat: error={}",
          e.getMessage(),
          e);
    }
  }

  private boolean send(String message) {
    try {
      notificationService.sendMessage(message, INVESTMENT);
      return true;
    } catch (RuntimeException e) {
      log.error(
          "Failed to send SEB fund confirmation digest to Slack: error={}", e.getMessage(), e);
      return false;
    }
  }

  private record NewFindings(SebFundConfirmationOutcome outcome, List<String> findings) {}
}
