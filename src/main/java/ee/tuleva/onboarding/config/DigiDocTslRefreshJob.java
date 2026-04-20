package ee.tuleva.onboarding.config;

import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.sentry.Sentry;
import io.sentry.SentryLevel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.digidoc4j.Configuration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class DigiDocTslRefreshJob {

  private static final int MAX_ATTEMPTS = 3;

  private final Configuration digiDocConfiguration;

  long backoffBaseSeconds = 10;

  @Async
  @EventListener(ApplicationReadyEvent.class)
  public void refreshTslOnStartup() {
    refreshWithRetry();
  }

  @Scheduled(cron = "0 */30 * * * *")
  public void scheduledRefreshTsl() {
    refreshWithRetry();
  }

  private void refreshWithRetry() {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        digiDocConfiguration.getTSL().refresh();
        log.info("TSL refresh succeeded: attempt={}", attempt);
        return;
      } catch (Exception e) {
        if (attempt < MAX_ATTEMPTS) {
          long sleepSeconds = backoffBaseSeconds * (long) Math.pow(3, attempt - 1);
          log.warn(
              "TSL refresh failed: attempt={}, error={}, retrying in {}s",
              attempt,
              e.getMessage(),
              sleepSeconds);
          sleep(sleepSeconds);
        } else {
          log.error("TSL refresh failed after all retries: attempts={}", MAX_ATTEMPTS, e);
          reportToSentry(e);
        }
      }
    }
  }

  private void reportToSentry(Exception cause) {
    Sentry.withScope(
        scope -> {
          scope.setLevel(SentryLevel.FATAL);
          scope.setTag("action", "redeploy");
          scope.setTag("component", "digidoc4j-tsl");
          scope.setFingerprint(List.of("tsl-refresh-exhausted-retries"));
          scope.setExtra("cause", String.valueOf(cause));
          Sentry.captureMessage(
              "TSL refresh failed on startup — signing will fail until redeploy. "
                  + "Action: force a new ECS deployment of onboarding-service-production.");
        });
  }

  private void sleep(long seconds) {
    try {
      SECONDS.sleep(seconds);
    } catch (InterruptedException e) {
      currentThread().interrupt();
    }
  }
}
