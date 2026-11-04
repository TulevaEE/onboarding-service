package ee.tuleva.onboarding.investment.transaction.ingest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;

final class RecordingLockProvider implements ExtensibleLockProvider {

  private enum Availability {
    FREE,
    HELD_ELSEWHERE
  }

  private final Availability availability;
  private final List<LockConfiguration> requests = new ArrayList<>();
  private boolean held;

  private RecordingLockProvider(Availability availability) {
    this.availability = availability;
  }

  static RecordingLockProvider free() {
    return new RecordingLockProvider(Availability.FREE);
  }

  static RecordingLockProvider heldElsewhere() {
    return new RecordingLockProvider(Availability.HELD_ELSEWHERE);
  }

  @Override
  public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
    requests.add(lockConfiguration);
    if (availability == Availability.HELD_ELSEWHERE) {
      return Optional.empty();
    }
    held = true;
    return Optional.of(() -> held = false);
  }

  boolean isHeld() {
    return held;
  }

  List<String> lockNames() {
    return requests.stream().map(LockConfiguration::getName).toList();
  }

  List<Duration> lockAtMostFor() {
    return requests.stream().map(LockConfiguration::getLockAtMostFor).toList();
  }
}
