package ee.tuleva.onboarding.investment.report.publishing;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "investment-report-publishing.enabled", havingValue = "true")
@RequiredArgsConstructor
class InvestmentReportPublishingLock {

  static final String ONE_PUBLISH_AT_A_TIME_LOCK = "investmentReportPublishing";
  private static final Duration PUBLISH_LOCK_HELD_AT_MOST = Duration.ofMinutes(30);

  private final LockProvider lockProvider;
  private final Clock clock;

  <T> Optional<T> whileNoOtherPublishRuns(Supplier<T> publish) {
    var lock =
        lockProvider.lock(
            new LockConfiguration(
                clock.instant(),
                ONE_PUBLISH_AT_A_TIME_LOCK,
                PUBLISH_LOCK_HELD_AT_MOST,
                Duration.ZERO));
    if (lock.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(publish.get());
    } finally {
      lock.get().unlock();
    }
  }
}
