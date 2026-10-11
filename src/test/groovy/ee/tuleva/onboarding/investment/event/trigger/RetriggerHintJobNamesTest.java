package ee.tuleva.onboarding.investment.event.trigger;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.pipeline.PipelineNotifier;
import ee.tuleva.onboarding.pipeline.PipelineRun;
import ee.tuleva.onboarding.pipeline.PipelineStep;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RetriggerHintJobNamesTest {

  private static final Pattern RETRIGGER_INSERT =
      Pattern.compile("INSERT INTO investment_job_trigger \\(job_name\\) VALUES \\('([^']+)'\\);");

  @Test
  void everyJobTheFailureAlertTellsTheOperatorToTriggerIsOneThePollerRuns() {
    var hintedJobs =
        Stream.of(PipelineRun.PipelineType.values())
            .flatMap(type -> allSteps().map(step -> failureAlert(type, step)))
            .flatMap(alert -> RETRIGGER_INSERT.matcher(alert).results())
            .map(match -> match.group(1))
            .collect(toSet());

    assertThat(hintedJobs).isNotEmpty();
    assertThat(JobTriggerPoller.EVENTS.keySet()).containsAll(hintedJobs);
  }

  private static Stream<String> allSteps() {
    return Stream.concat(PipelineStep.IMPORT_PIPELINE.stream(), PipelineStep.NAV_PIPELINE.stream());
  }

  private static String failureAlert(PipelineRun.PipelineType type, String step) {
    var notifications = new RecordingNotificationService();
    var pipeline = new PipelineRun(type, "trigger");
    pipeline.markHealthNotificationFired();
    pipeline.stepStarted(step);
    pipeline.stepFailed(step, "boom");

    new PipelineNotifier(notifications).sendCompleted(pipeline);

    return String.join("\n", notifications.messages);
  }

  private static final class RecordingNotificationService implements OperationsNotificationService {

    private final List<String> messages = new ArrayList<>();

    @Override
    public void sendMessage(String message, Channel channel) {
      messages.add(message);
    }

    @Override
    public void sendMessage(String message, Channel channel, Severity severity) {
      messages.add(message);
    }
  }
}
