package ee.tuleva.onboarding.investment.event.trigger;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class JobTriggerLease {

  private final ThreadLocal<Instant> deadline = new ThreadLocal<>();

  public void runUntil(Instant deadline, Runnable work) {
    this.deadline.set(deadline);
    try {
      work.run();
    } finally {
      this.deadline.remove();
    }
  }

  public Optional<Instant> deadline() {
    return Optional.ofNullable(deadline.get());
  }
}
