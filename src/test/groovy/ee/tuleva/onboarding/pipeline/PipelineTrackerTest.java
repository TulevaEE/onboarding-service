package ee.tuleva.onboarding.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PipelineTrackerTest {

  private final PipelineTracker tracker = new PipelineTracker();

  @AfterEach
  void tearDown() {
    tracker.clear();
  }

  @Test
  void startCreatesPipelineRun() {
    var run = tracker.start(PipelineRun.PipelineType.IMPORT, "cron:15:00");

    assertThat(run).isNotNull();
    assertThat(run.getTrigger()).isEqualTo("cron:15:00");
    assertThat(tracker.current()).isSameAs(run);
  }

  @Test
  void tracksStepLifecycle() {
    tracker.start(PipelineRun.PipelineType.IMPORT, "test");

    tracker.stepStarted("Step A");
    tracker.stepCompleted("Step A");

    var run = tracker.current();
    assertThat(run.getSteps()).hasSize(1);
    assertThat(run.getSteps().getFirst().getName()).isEqualTo("Step A");
    assertThat(run.getSteps().getFirst().getStatus()).isEqualTo(PipelineRun.StepStatus.COMPLETED);
  }

  @Test
  void tracksStepFailure() {
    tracker.start(PipelineRun.PipelineType.IMPORT, "test");

    tracker.stepStarted("Step A");
    tracker.stepFailed("Step A", "something broke");

    var run = tracker.current();
    assertThat(run.hasFailure()).isTrue();
    assertThat(run.firstFailure()).isPresent();
    assertThat(run.firstFailure().get().getError()).isEqualTo("something broke");
  }

  @Test
  void markChangedMarksTheCurrentRunAsChanged() {
    tracker.start(PipelineRun.PipelineType.IMPORT, "test");

    tracker.markChanged();

    assertThat(tracker.current().isChanged()).isTrue();
  }

  @Test
  void markChangedIsANoOpWhenNoPipeline() {
    tracker.markChanged();

    assertThat(tracker.current()).isNull();
  }

  @Test
  void markHealthNotificationFiredMarksTheCurrentRun() {
    tracker.start(PipelineRun.PipelineType.IMPORT, "test");

    tracker.markHealthNotificationFired();

    assertThat(tracker.current().isHealthNotificationFired()).isTrue();
  }

  @Test
  void markHealthNotificationFiredIsANoOpWhenNoPipeline() {
    tracker.markHealthNotificationFired();

    assertThat(tracker.current()).isNull();
  }

  @Test
  void stepCompletedWithDetailRecordsTheDetailOnTheCurrentRun() {
    tracker.start(PipelineRun.PipelineType.IMPORT, "test");
    tracker.stepStarted("Step A");

    tracker.stepCompleted("Step A", "12 files");

    var step = tracker.current().getSteps().getFirst();
    assertThat(step.getStatus()).isEqualTo(PipelineRun.StepStatus.COMPLETED);
    assertThat(step.getDetail()).isEqualTo("12 files");
  }

  @Test
  void stepCompletedWithDetailIsANoOpWhenNoPipeline() {
    tracker.stepCompleted("Step A", "12 files");

    assertThat(tracker.current()).isNull();
  }

  @Test
  void clearRemovesThreadLocal() {
    tracker.start(PipelineRun.PipelineType.IMPORT, "test");
    tracker.clear();

    assertThat(tracker.current()).isNull();
  }

  @Test
  void stepOperationsAreNoOpWhenNoPipeline() {
    tracker.stepStarted("Step A");
    tracker.stepCompleted("Step A");
    tracker.stepFailed("Step B", "error");

    assertThat(tracker.current()).isNull();
  }
}
