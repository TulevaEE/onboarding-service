package ee.tuleva.onboarding.banking.seb;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Fallback;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

@Configuration
public class TestSebSchedulerConfiguration {

  @Bean
  @Fallback
  public TaskScheduler taskScheduler() {
    return new ImmediateTaskScheduler();
  }

  private static class ImmediateTaskScheduler implements TaskScheduler {

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
      task.run();
      return null;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
      task.run();
      return null;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        Runnable task, Instant startTime, Duration period) {
      task.run();
      return null;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
      task.run();
      return null;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable task, Instant startTime, Duration delay) {
      task.run();
      return null;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
      task.run();
      return null;
    }
  }
}
