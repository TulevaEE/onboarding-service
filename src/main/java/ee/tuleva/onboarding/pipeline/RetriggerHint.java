package ee.tuleva.onboarding.pipeline;

import static java.util.stream.Collectors.joining;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

final class RetriggerHint {

  private RetriggerHint() {}

  static String forFailedStep(PipelineRun pipeline, String stepName) {
    return switch (stepName) {
      case PipelineStep.NAV_CALCULATION, PipelineStep.REPORT_PERSIST, PipelineStep.REPORT_EMAIL ->
          navRerunHint(LocalDate.ofInstant(pipeline.getStartedAt(), ZoneId.of("Europe/Tallinn")));
      default -> "Fix and re-trigger:\n" + jobTriggerInsert(jobNameForStep(stepName));
    };
  }

  private static String navRerunHint(LocalDate calculationDate) {
    var checksFollowingNavPublication =
        List.of("LimitCheckJob", "FeeCheckJob", "PortfolioReconciliationJob");
    return """
        NavSelfHealJob retries an unpublished NAV during the working day. To re-run a failed fund now:
        POST /admin/calculate-nav?fundCode=<fund code>&date=%s&publish=true (header X-Admin-Token)
        That publishes the NAV without the checks that follow it; re-run them with:
        %s"""
        .formatted(
            calculationDate,
            checksFollowingNavPublication.stream()
                .map(RetriggerHint::jobTriggerInsert)
                .collect(joining("\n")));
  }

  private static String jobTriggerInsert(String jobName) {
    return "INSERT INTO investment_job_trigger (job_name) VALUES ('%s');".formatted(jobName);
  }

  private static String jobNameForStep(String stepName) {
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
