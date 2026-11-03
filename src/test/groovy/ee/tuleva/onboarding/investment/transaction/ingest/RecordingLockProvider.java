package ee.tuleva.onboarding.investment.transaction.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

final class RecordingLockProvider implements LockProvider {

  private final List<String> lockNames = new ArrayList<>();
  private final Optional<SimpleLock> answer;

  private RecordingLockProvider(Optional<SimpleLock> answer) {
    this.answer = answer;
  }

  static RecordingLockProvider free() {
    return new RecordingLockProvider(Optional.of(() -> {}));
  }

  static RecordingLockProvider heldElsewhere() {
    return new RecordingLockProvider(Optional.empty());
  }

  @Override
  public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
    lockNames.add(lockConfiguration.getName());
    return answer;
  }

  List<String> lockNames() {
    return List.copyOf(lockNames);
  }
}
