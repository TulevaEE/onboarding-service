package ee.tuleva.onboarding.investment.event;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineNotifierTest {

  @Mock OperationsNotificationService notificationService;

  @InjectMocks PipelineNotifier notifier;

  @Test
  void sendCompletedSuccessSendsCompactMessage() {
    var pipeline = new PipelineRun(PipelineRun.PipelineType.IMPORT, "cron:15:00");
    pipeline.stepStarted("Report Import");
    pipeline.stepCompleted("Report Import");
    pipeline.stepStarted("Position Import");
    pipeline.stepCompleted("Position Import");

    notifier.sendCompleted(pipeline);

    then(notificationService).should().sendMessage(contains("Import pipeline"), eq(INVESTMENT));
  }

  @Test
  void sendCompletedFailureSendsFullBreakdown() {
    var pipeline = new PipelineRun(PipelineRun.PipelineType.IMPORT, "cron:15:00");
    pipeline.stepStarted("Report Import");
    pipeline.stepCompleted("Report Import");
    pipeline.stepStarted("Position Import");
    pipeline.stepFailed("Position Import", "DB connection lost");

    notifier.sendCompleted(pipeline);

    then(notificationService).should().sendMessage(contains("FAILED"), eq(INVESTMENT));
    then(notificationService)
        .should()
        .sendMessage(contains("FundPositionImportJob"), eq(INVESTMENT));
    then(notificationService).should().sendMessage(contains("skipped"), eq(INVESTMENT));
  }
}
