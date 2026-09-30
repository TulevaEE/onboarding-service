package ee.tuleva.onboarding.pipeline;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineNotifier {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final List<String> CHECKS_FOLLOWING_NAV_PUBLICATION =
      List.of("LimitCheckJob", "FeeCheckJob", "PortfolioReconciliationJob");

  private final OperationsNotificationService notificationService;

  public void sendCompleted(@Nullable PipelineRun pipeline) {
    if (pipeline == null) {
      log.error("No current pipeline run to send a completion notification for");
      return;
    }
    try {
      if (pipeline.hasFailure()) {
        sendFailure(pipeline);
      } else if (pipeline.isChanged() || pipeline.getType() == PipelineRun.PipelineType.NAV) {
        sendSuccess(pipeline);
      }
    } catch (Exception e) {
      log.error("Failed to send pipeline notification", e);
    }
  }

  private void sendSuccess(PipelineRun pipeline) {
    var stepDetails =
        pipeline.getSteps().stream()
            .map(this::formatStepCompact)
            .collect(Collectors.joining(" → "));

    var label = pipelineLabel(pipeline);
    var message =
        "✅ %s pipeline%s (%s) — %s"
            .formatted(
                label,
                triggerSourceTag(pipeline),
                formatDuration(pipeline.totalDuration()),
                stepDetails);

    notificationService.sendMessage(message, INVESTMENT);
  }

  private void sendFailure(PipelineRun pipeline) {
    if (isDuplicateHealthCheckBlock(pipeline)) {
      return;
    }
    var steps = resolveSteps(pipeline);
    var message = new StringBuilder();
    message.append(
        "❌ %s PIPELINE%s FAILED\n".formatted(pipelineLabel(pipeline), triggerSourceTag(pipeline)));

    Set<String> completedStepNames =
        pipeline.getSteps().stream()
            .map(PipelineRun.StepResult::getName)
            .collect(Collectors.toSet());

    for (var step : pipeline.getSteps()) {
      message.append("\n").append(formatStep(step));
    }

    for (var stepName : steps) {
      if (!completedStepNames.contains(stepName)) {
        message.append("\n⏭\uFE0F %s (skipped)".formatted(stepName));
      }
    }

    var failed = pipeline.firstFailure().orElseThrow();
    message.append("\n\nChain stopped. ").append(retriggerHint(pipeline, failed.getName()));

    notificationService.sendMessage(message.toString(), INVESTMENT);
  }

  private boolean isDuplicateHealthCheckBlock(PipelineRun pipeline) {
    if (pipeline.isHealthNotificationFired()) {
      return false;
    }
    var failures =
        pipeline.getSteps().stream()
            .filter(s -> s.getStatus() == PipelineRun.StepStatus.FAILED)
            .toList();
    return !failures.isEmpty()
        && failures.stream().allMatch(s -> s.getName().equals(PipelineStep.HEALTH_CHECK));
  }

  private String pipelineLabel(PipelineRun pipeline) {
    return switch (pipeline.getType()) {
      case IMPORT -> "Import";
      case NAV -> pipeline.getTrigger();
    };
  }

  private String triggerSourceTag(PipelineRun pipeline) {
    return switch (pipeline.getTriggerSource()) {
      case SCHEDULED -> "";
      case SELF_HEAL -> " [self-heal]";
      case MANUAL -> " [manual]";
    };
  }

  private List<String> resolveSteps(PipelineRun pipeline) {
    return switch (pipeline.getType()) {
      case NAV -> PipelineStep.NAV_PIPELINE;
      case IMPORT -> PipelineStep.IMPORT_PIPELINE;
    };
  }

  private String formatStepCompact(PipelineRun.StepResult step) {
    var duration = formatDuration(step.duration());
    if (step.getDetail() != null) {
      return "%s (%s, %s)".formatted(step.getName(), duration, step.getDetail());
    }
    return "%s (%s)".formatted(step.getName(), duration);
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

  private String retriggerHint(PipelineRun pipeline, String stepName) {
    return switch (stepName) {
      case PipelineStep.NAV_CALCULATION, PipelineStep.REPORT_PERSIST, PipelineStep.REPORT_EMAIL ->
          navRerunHint(LocalDate.ofInstant(pipeline.getStartedAt(), TALLINN));
      default -> "Fix and re-trigger:\n" + jobTriggerInsert(jobNameForStep(stepName));
    };
  }

  private String navRerunHint(LocalDate calculationDate) {
    return """
        NavSelfHealJob retries an unpublished NAV during the working day. To re-run a failed fund now:
        POST /admin/calculate-nav?fundCode=<fund code>&date=%s&publish=true (header X-Admin-Token)
        That publishes the NAV without the checks that follow it; re-run them with:
        %s"""
        .formatted(
            calculationDate,
            CHECKS_FOLLOWING_NAV_PUBLICATION.stream()
                .map(this::jobTriggerInsert)
                .collect(Collectors.joining("\n")));
  }

  private String jobTriggerInsert(String jobName) {
    return "INSERT INTO investment_job_trigger (job_name) VALUES ('%s');".formatted(jobName);
  }

  private String jobNameForStep(String stepName) {
    return switch (stepName) {
      case PipelineStep.REPORT_IMPORT -> "ReportImportJob";
      case PipelineStep.POSITION_IMPORT -> "FundPositionImportJob";
      case PipelineStep.FEE_ACCRUAL_SYNC -> "FeeAccrualPositionSyncJob";
      case PipelineStep.LIMIT_CHECK -> "LimitCheckJob";
      case PipelineStep.FEE_CHECK -> "FeeCheckJob";
      case PipelineStep.HEALTH_CHECK -> "FundPositionImportJob";
      case PipelineStep.EXECUTION_MATCHING -> "SebPendingTransactionReconciliationJob";
      case PipelineStep.TRACKING_DIFFERENCE -> "TrackingDifferenceJob";
      default -> stepName;
    };
  }
}
