package ee.tuleva.onboarding.investment.event;

import static ee.tuleva.onboarding.time.ClockHolder.clock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

@Getter
public class PipelineRun {

  private final PipelineType type;
  private final String trigger;
  private final Instant startedAt;
  private final List<StepResult> steps = new ArrayList<>();
  private boolean changed;
  private boolean healthNotificationFired;

  public PipelineRun(PipelineType type, String trigger) {
    this.type = type;
    this.trigger = trigger;
    this.startedAt = clock().instant();
  }

  public enum PipelineType {
    IMPORT,
    NAV
  }

  public void markChanged() {
    this.changed = true;
  }

  public void markHealthNotificationFired() {
    this.healthNotificationFired = true;
  }

  public void stepStarted(String name) {
    steps.add(new StepResult(name, clock().instant()));
  }

  public void stepCompleted(String name) {
    findStep(name).ifPresent(step -> step.complete(clock().instant()));
  }

  public void stepCompleted(String name, String detail) {
    findStep(name).ifPresent(step -> step.complete(clock().instant(), detail));
  }

  public void stepFailed(String name, String error) {
    findStep(name).ifPresent(step -> step.fail(clock().instant(), error));
  }

  public Duration totalDuration() {
    return Duration.between(startedAt, clock().instant());
  }

  public boolean hasFailure() {
    return steps.stream().anyMatch(s -> s.status == StepStatus.FAILED);
  }

  public Optional<StepResult> firstFailure() {
    return steps.stream().filter(s -> s.status == StepStatus.FAILED).findFirst();
  }

  private Optional<StepResult> findStep(String name) {
    return steps.stream().filter(s -> s.name.equals(name)).findFirst();
  }

  public enum StepStatus {
    RUNNING,
    COMPLETED,
    FAILED
  }

  @Getter
  public static class StepResult {
    private final String name;
    private final Instant startedAt;
    private Instant completedAt;
    private StepStatus status;
    private String error;
    private String detail;

    StepResult(String name, Instant startedAt) {
      this.name = name;
      this.startedAt = startedAt;
      this.status = StepStatus.RUNNING;
    }

    void complete(Instant at) {
      this.completedAt = at;
      this.status = StepStatus.COMPLETED;
    }

    void complete(Instant at, String detail) {
      this.completedAt = at;
      this.status = StepStatus.COMPLETED;
      this.detail = detail;
    }

    void fail(Instant at, String error) {
      this.completedAt = at;
      this.status = StepStatus.FAILED;
      this.error = error;
    }

    public Duration duration() {
      if (completedAt == null) {
        return Duration.ZERO;
      }
      return Duration.between(startedAt, completedAt);
    }
  }
}
