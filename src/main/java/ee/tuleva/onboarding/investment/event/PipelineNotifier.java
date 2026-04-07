package ee.tuleva.onboarding.investment.event;

import static ee.tuleva.onboarding.investment.event.PipelineRun.StepStatus.*;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineNotifier {

  private final OperationsNotificationService notificationService;

  public void sendStarted(PipelineRun pipeline) {
    try {
      var steps = resolveSteps(pipeline);
      var message = new StringBuilder();
      message.append("INVESTMENT PIPELINE [%s]\n".formatted(pipeline.getTrigger()));

      var firstStep = steps.getFirst();
      for (var stepName : steps) {
        if (stepName.equals(firstStep)) {
          message.append("\n\uD83D\uDD04 %s...".formatted(stepName));
        } else {
          message.append("\n⏳ %s".formatted(stepName));
        }
      }

      notificationService.sendMessage(message.toString(), INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send pipeline start notification", e);
    }
  }

  public void sendCompleted(PipelineRun pipeline) {
    try {
      var steps = resolveSteps(pipeline);
      var message = new StringBuilder();
      message.append("INVESTMENT PIPELINE [%s]\n".formatted(pipeline.getTrigger()));

      Set<String> completedStepNames =
          pipeline.getSteps().stream()
              .map(PipelineRun.StepResult::getName)
              .collect(Collectors.toSet());

      for (var step : pipeline.getSteps()) {
        message.append("\n").append(formatStep(step));
      }

      for (var stepName : steps) {
        if (!completedStepNames.contains(stepName)) {
          if (pipeline.hasFailure()) {
            message.append("\n⏭\uFE0F %s (skipped)".formatted(stepName));
          }
        }
      }

      if (pipeline.hasFailure()) {
        var failed = pipeline.firstFailure().orElseThrow();
        message.append(
            "\n\nChain stopped. Fix and re-trigger:\nINSERT INTO investment_job_trigger (job_name) VALUES ('%s');"
                .formatted(jobNameForStep(failed.getName())));
      } else {
        message.append("\n\nDone in %s".formatted(formatDuration(pipeline.totalDuration())));
      }

      notificationService.sendMessage(message.toString(), INVESTMENT);
    } catch (Exception e) {
      log.error("Failed to send pipeline completion notification", e);
    }
  }

  private List<String> resolveSteps(PipelineRun pipeline) {
    return switch (pipeline.getType()) {
      case NAV -> PipelineStep.NAV_PIPELINE;
      case IMPORT -> PipelineStep.IMPORT_PIPELINE;
    };
  }

  private String formatStep(PipelineRun.StepResult step) {
    return switch (step.getStatus()) {
      case COMPLETED -> "✅ %s (%s)".formatted(step.getName(), formatDuration(step.duration()));
      case FAILED ->
          "❌ %s FAILED (%s)\n     %s"
              .formatted(step.getName(), formatDuration(step.duration()), step.getError());
      case RUNNING -> "\uD83D\uDD04 %s...".formatted(step.getName());
    };
  }

  private String formatDuration(Duration duration) {
    long totalSeconds = duration.toSeconds();
    if (totalSeconds < 60) {
      return "%ds".formatted(totalSeconds);
    }
    long minutes = totalSeconds / 60;
    long seconds = totalSeconds % 60;
    return "%dm %ds".formatted(minutes, seconds);
  }

  private String jobNameForStep(String stepName) {
    return switch (stepName) {
      case PipelineStep.REPORT_IMPORT -> "ReportImportJob";
      case PipelineStep.POSITION_IMPORT -> "FundPositionImportJob";
      case PipelineStep.FEE_ACCRUAL_SYNC -> "FeeAccrualPositionSyncJob";
      case PipelineStep.NAV_CALCULATION -> "NavCalculationJob";
      case PipelineStep.LIMIT_CHECK -> "LimitCheckJob";
      case PipelineStep.TRACKING_DIFFERENCE -> "TrackingDifferenceJob";
      default -> stepName;
    };
  }
}
