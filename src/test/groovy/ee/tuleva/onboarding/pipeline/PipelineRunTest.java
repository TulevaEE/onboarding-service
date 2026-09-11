package ee.tuleva.onboarding.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.time.ClockHolder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PipelineRunTest {

  @AfterEach
  void resetClock() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void durationIsZeroWhileTheStepIsStillRunning() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    var pipeline = new PipelineRun(PipelineRun.PipelineType.IMPORT, "cron:15:00");

    pipeline.stepStarted("Report Import");

    assertThat(pipeline.getSteps().getFirst().duration()).isEqualTo(Duration.ZERO);
  }

  @Test
  void durationIsTheGapBetweenStartAndCompletion() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    var pipeline = new PipelineRun(PipelineRun.PipelineType.IMPORT, "cron:15:00");

    pipeline.stepStarted("Report Import");
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:07Z"), ZoneOffset.UTC));
    pipeline.stepCompleted("Report Import");

    assertThat(pipeline.getSteps().getFirst().duration()).isEqualTo(Duration.ofSeconds(7));
    assertThat(pipeline.getSteps().getFirst().getStatus())
        .isEqualTo(PipelineRun.StepStatus.COMPLETED);
  }

  @Test
  void stepCompletedWithDetailRecordsTheDetailAndCompletionTime() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    var pipeline = new PipelineRun(PipelineRun.PipelineType.IMPORT, "cron:15:00");

    pipeline.stepStarted("Report Import");
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:03Z"), ZoneOffset.UTC));
    pipeline.stepCompleted("Report Import", "12 files");

    var step = pipeline.getSteps().getFirst();
    assertThat(step.getStatus()).isEqualTo(PipelineRun.StepStatus.COMPLETED);
    assertThat(step.getDetail()).isEqualTo("12 files");
    assertThat(step.duration()).isEqualTo(Duration.ofSeconds(3));
  }

  @Test
  void stepCompletedIsANoOpForAnUnknownStepName() {
    var pipeline = new PipelineRun(PipelineRun.PipelineType.IMPORT, "cron:15:00");
    pipeline.stepStarted("Report Import");

    pipeline.stepCompleted("Unknown Step");

    assertThat(pipeline.getSteps().getFirst().getStatus())
        .isEqualTo(PipelineRun.StepStatus.RUNNING);
  }
}
