package ee.tuleva.onboarding.investment.event;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class PipelineTracker {

  private static final ThreadLocal<PipelineRun> CURRENT = new ThreadLocal<>();

  public PipelineRun start(PipelineRun.PipelineType type, String trigger) {
    var run = new PipelineRun(type, trigger);
    CURRENT.set(run);
    return run;
  }

  public void stepStarted(String name) {
    var run = CURRENT.get();
    if (run != null) {
      run.stepStarted(name);
    }
  }

  public void stepCompleted(String name) {
    var run = CURRENT.get();
    if (run != null) {
      run.stepCompleted(name);
    }
  }

  public void stepFailed(String name, String error) {
    var run = CURRENT.get();
    if (run != null) {
      run.stepFailed(name, error);
    }
  }

  public @Nullable PipelineRun current() {
    return CURRENT.get();
  }

  public void clear() {
    CURRENT.remove();
  }
}
