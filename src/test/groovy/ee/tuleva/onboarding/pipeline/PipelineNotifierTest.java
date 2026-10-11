package ee.tuleva.onboarding.pipeline;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineNotifierTest {

  @Mock OperationsNotificationService notificationService;

  @InjectMocks PipelineNotifier notifier;

  @AfterEach
  void resetClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void sendCompletedSuccessSendsCompactMessage() {
    var pipeline = new PipelineRun(PipelineRun.PipelineType.IMPORT, "cron:15:00");
    pipeline.markChanged();
    pipeline.stepStarted("Report Import");
    pipeline.stepCompleted("Report Import");
    pipeline.stepStarted("Position Import");
    pipeline.stepCompleted("Position Import");

    notifier.sendCompleted(pipeline);

    then(notificationService).should().sendMessage(contains("Import pipeline"), eq(INVESTMENT));
  }

  @Test
  void sendCompletedSuccessSilentWhenNoChanges() {
    var pipeline = new PipelineRun(PipelineRun.PipelineType.IMPORT, "cron:15:00");
    pipeline.stepStarted("Report Import");
    pipeline.stepCompleted("Report Import");

    notifier.sendCompleted(pipeline);

    then(notificationService).shouldHaveNoInteractions();
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

  @Test
  void skipsFailureMessageWhenOnlyHealthCheckFailedAndNoFreshHealthNotification() {
    var pipeline = new PipelineRun(PipelineRun.PipelineType.IMPORT, "cron:15:00");
    pipeline.stepStarted("Report Import");
    pipeline.stepCompleted("Report Import");
    pipeline.stepStarted("Position Import");
    pipeline.stepCompleted("Position Import");
    pipeline.stepStarted("Health Check");
    pipeline.stepFailed("Health Check", "Import blocked: provider=SEB, date=2026-04-23");

    notifier.sendCompleted(pipeline);

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void sendsFailureMessageWhenHealthCheckFailedAndFreshNotificationFired() {
    var pipeline = new PipelineRun(PipelineRun.PipelineType.IMPORT, "cron:15:00");
    pipeline.stepStarted("Report Import");
    pipeline.stepCompleted("Report Import");
    pipeline.stepStarted("Position Import");
    pipeline.stepCompleted("Position Import");
    pipeline.stepStarted("Health Check");
    pipeline.stepFailed("Health Check", "Import blocked: provider=SEB, date=2026-04-23");
    pipeline.markHealthNotificationFired();

    notifier.sendCompleted(pipeline);

    then(notificationService).should().sendMessage(contains("FAILED"), eq(INVESTMENT));
  }

  @Test
  void sendsFailureMessageWhenNonHealthStepAlsoFailed() {
    var pipeline = new PipelineRun(PipelineRun.PipelineType.IMPORT, "cron:15:00");
    pipeline.stepStarted("Report Import");
    pipeline.stepFailed("Report Import", "S3 unreachable");

    notifier.sendCompleted(pipeline);

    then(notificationService).should().sendMessage(contains("FAILED"), eq(INVESTMENT));
  }

  @Test
  void navFailureShowsTrackingDifferenceAndLimitCheckAsSkippedWhenNotRun() {
    var pipeline = new PipelineRun(PipelineRun.PipelineType.NAV, "NAV TUK75");
    pipeline.stepStarted("NAV Calculation");
    pipeline.stepFailed("NAV Calculation", "boom");

    notifier.sendCompleted(pipeline);

    then(notificationService)
        .should()
        .sendMessage(contains("Tracking Difference (skipped)"), eq(INVESTMENT));
    then(notificationService)
        .should()
        .sendMessage(contains("Limit Check (skipped)"), eq(INVESTMENT));
  }

  @Test
  void aFailedFeeCheckTellsTheOperatorToTriggerTheFeeCheckJob() {
    var pipeline = new PipelineRun(PipelineRun.PipelineType.NAV, "NAV TUK75");
    pipeline.stepStarted(PipelineStep.FEE_CHECK);
    pipeline.stepFailed(PipelineStep.FEE_CHECK, "boom");

    notifier.sendCompleted(pipeline);

    then(notificationService)
        .should()
        .sendMessage(contains("VALUES ('FeeCheckJob');"), eq(INVESTMENT));
  }

  @Test
  void aNavStepFailurePointsTheOperatorAtTheAdminNavEndpointAndTheChecksItSkips() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-15T09:10:00Z"), ZoneOffset.UTC));
    for (var step :
        List.of(
            PipelineStep.NAV_CALCULATION, PipelineStep.REPORT_PERSIST, PipelineStep.REPORT_EMAIL)) {
      var stepNotificationService = mock(OperationsNotificationService.class);
      var pipeline = new PipelineRun(PipelineRun.PipelineType.NAV, "NAV TUK75");
      pipeline.stepStarted(step);
      pipeline.stepFailed(step, "boom");

      new PipelineNotifier(stepNotificationService).sendCompleted(pipeline);

      then(stepNotificationService)
          .should()
          .sendMessage(
              endsWith(
                  """


                  Chain stopped. NavSelfHealJob retries an unpublished NAV during the working day. To re-run a failed fund now:
                  POST /admin/calculate-nav?fundCode=<fund code>&date=2026-01-15&publish=true (header X-Admin-Token)
                  That publishes the NAV without the checks that follow it; re-run them with:
                  INSERT INTO investment_job_trigger (job_name) VALUES ('LimitCheckJob');
                  INSERT INTO investment_job_trigger (job_name) VALUES ('FeeCheckJob');
                  INSERT INTO investment_job_trigger (job_name) VALUES ('PortfolioReconciliationJob');"""),
              eq(INVESTMENT));
    }
  }

  @Test
  void totalDurationJustUnderAMinuteIsFormattedInSeconds() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    var pipeline = new PipelineRun(PipelineRun.PipelineType.IMPORT, "cron:15:00");
    pipeline.markChanged();
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:59Z"), ZoneOffset.UTC));

    notifier.sendCompleted(pipeline);

    then(notificationService).should().sendMessage(contains("(59s)"), eq(INVESTMENT));
  }

  @Test
  void totalDurationOfExactlyAMinuteRollsOverToMinutesAndSeconds() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    var pipeline = new PipelineRun(PipelineRun.PipelineType.IMPORT, "cron:15:00");
    pipeline.markChanged();
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-01T00:01:00Z"), ZoneOffset.UTC));

    notifier.sendCompleted(pipeline);

    then(notificationService).should().sendMessage(contains("(1m 0s)"), eq(INVESTMENT));
  }

  @Test
  void totalDurationOverAMinuteSplitsMinutesAndSecondsCorrectly() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    var pipeline = new PipelineRun(PipelineRun.PipelineType.IMPORT, "cron:15:00");
    pipeline.markChanged();
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-01T00:02:05Z"), ZoneOffset.UTC));

    notifier.sendCompleted(pipeline);

    then(notificationService).should().sendMessage(contains("(2m 5s)"), eq(INVESTMENT));
  }

  @Test
  void sendCompletedSuccessIncludesStepDetailOnlyWhenPresent() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    var pipeline = new PipelineRun(PipelineRun.PipelineType.IMPORT, "cron:15:00");
    pipeline.markChanged();
    pipeline.stepStarted("Report Import");
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:03Z"), ZoneOffset.UTC));
    pipeline.stepCompleted("Report Import", "12 files");
    pipeline.stepStarted("Position Import");
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:05Z"), ZoneOffset.UTC));
    pipeline.stepCompleted("Position Import");

    notifier.sendCompleted(pipeline);

    then(notificationService)
        .should()
        .sendMessage(contains("Report Import (3s, 12 files)"), eq(INVESTMENT));
    then(notificationService)
        .should()
        .sendMessage(contains("Position Import (2s)"), eq(INVESTMENT));
  }

  @Test
  void sendCompletedFailureFormatsEachStepAccordingToItsStatus() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    var pipeline = new PipelineRun(PipelineRun.PipelineType.IMPORT, "cron:15:00");
    pipeline.stepStarted("Report Import");
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:05Z"), ZoneOffset.UTC));
    pipeline.stepCompleted("Report Import");
    pipeline.stepStarted("Position Import");
    pipeline.stepFailed("Position Import", "DB connection lost");
    pipeline.stepStarted("Fee Accrual Sync");

    notifier.sendCompleted(pipeline);

    then(notificationService)
        .should()
        .sendMessage(contains("✅ Report Import (5s)"), eq(INVESTMENT));
    then(notificationService)
        .should()
        .sendMessage(
            contains("❌ Position Import FAILED (0s)\n     DB connection lost"), eq(INVESTMENT));
    then(notificationService)
        .should()
        .sendMessage(contains("🔄 Fee Accrual Sync..."), eq(INVESTMENT));
  }

  @Test
  void selfHealTriggerSourceIsTaggedInTheMessage() {
    var pipeline =
        new PipelineRun(
            PipelineRun.PipelineType.IMPORT, "cron:15:00", PipelineRun.TriggerSource.SELF_HEAL);
    pipeline.markChanged();

    notifier.sendCompleted(pipeline);

    then(notificationService)
        .should()
        .sendMessage(contains("pipeline [self-heal] ("), eq(INVESTMENT));
  }

  @Test
  void manualTriggerSourceIsTaggedInTheMessage() {
    var pipeline =
        new PipelineRun(
            PipelineRun.PipelineType.IMPORT, "cron:15:00", PipelineRun.TriggerSource.MANUAL);
    pipeline.markChanged();

    notifier.sendCompleted(pipeline);

    then(notificationService).should().sendMessage(contains("pipeline [manual] ("), eq(INVESTMENT));
  }
}
