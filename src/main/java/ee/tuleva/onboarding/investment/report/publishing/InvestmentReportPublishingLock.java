package ee.tuleva.onboarding.investment.report.publishing;

import static java.time.ZoneOffset.UTC;

import ee.tuleva.onboarding.investment.report.publishing.wordpress.WordPressConfiguration;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "investment-report-publishing.enabled", havingValue = "true")
@RequiredArgsConstructor
class InvestmentReportPublishingLock {

  static final String ONE_PUBLISH_AT_A_TIME_LOCK = "investmentReportPublishing";

  // Per fund: page and attachment read by the check, the upload, the same two reads again
  // before repointing, the page lookup and the page update. Each call takes at most
  // 3 attempts × (5 s connect + 30 s read) + 2 × 2 s backoff = 109 s, so three funds hold
  // the lock at most 3 × 7 × 109 s = 38 min 9 s, plus 5 min to generate the reports.
  private static final int WORDPRESS_CALLS_PER_FUND = 7;
  private static final Duration GENERATING_THE_REPORTS_AT_MOST = Duration.ofMinutes(5);

  private final LockProvider lockProvider;
  private final JdbcClient jdbcClient;
  private final Clock clock;
  private final InvestmentReportPublishingProperties properties;

  <T> T whileNoOtherPublishRuns(Supplier<T> publish, Function<Instant, T> whileHeldUntil) {
    var lock =
        lockProvider.lock(
            new LockConfiguration(
                clock.instant(), ONE_PUBLISH_AT_A_TIME_LOCK, publishTakesAtMost(), Duration.ZERO));
    if (lock.isEmpty()) {
      return whileHeldUntil.apply(heldUntil());
    }
    try {
      return publish.get();
    } finally {
      lock.get().unlock();
    }
  }

  private Instant heldUntil() {
    return jdbcClient
        .sql("SELECT lock_until FROM shedlock WHERE name = :name")
        .param("name", ONE_PUBLISH_AT_A_TIME_LOCK)
        .query(LocalDateTime.class)
        .single()
        .toInstant(UTC);
  }

  private Duration publishTakesAtMost() {
    return WordPressConfiguration.CALL_TAKES_AT_MOST
        .multipliedBy((long) WORDPRESS_CALLS_PER_FUND * properties.funds().size())
        .plus(GENERATING_THE_REPORTS_AT_MOST);
  }
}
