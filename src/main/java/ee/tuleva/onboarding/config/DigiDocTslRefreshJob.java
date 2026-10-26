package ee.tuleva.onboarding.config;

import static ee.tuleva.onboarding.config.DigiDocTslRefreshJob.Trigger.SCHEDULED;
import static ee.tuleva.onboarding.config.DigiDocTslRefreshJob.Trigger.STARTUP;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.sentry.Sentry;
import io.sentry.SentryLevel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.digidoc4j.Configuration;
import org.digidoc4j.TSLCertificateSource;
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

  private static final int MAX_ATTEMPTS = 8;
  private static final String NO_TRUST_LIST_CONSEQUENCE =
      ": this task has no valid trust list, so it cannot finalize signatures until a refresh"
          + " succeeds. The scheduled refresh retries every 30 minutes, and a new ECS deployment"
          + " of onboarding-service-production retries now; neither helps while the trust list"
          + " endpoints themselves are down.";

  private final Configuration digiDocConfiguration;
  private final TrustListHealthIndicator trustList;

  long backoffBaseSeconds = 10;

  enum Trigger {
    STARTUP("startup", "TSL refresh failed on startup"),
    SCHEDULED("scheduled", "Scheduled TSL refresh failed");

    private final String tag;
    private final String failure;

    Trigger(String tag, String failure) {
      this.tag = tag;
      this.failure = failure;
    }
  }

  @Async
  @EventListener(ApplicationReadyEvent.class)
  public void refreshTslOnStartup() {
    refreshWithRetry(STARTUP);
  }

  @Scheduled(cron = "0 */30 * * * *")
  public void scheduledRefreshTsl() {
    refreshWithRetry(SCHEDULED);
  }

  private void refreshWithRetry(Trigger trigger) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        TSLCertificateSource tsl = digiDocConfiguration.getTSL();
        tsl.refresh();
        int certificates = requireCertificatesLoaded(tsl);
        trustList.trustListLoaded();
        log.info("TSL refresh succeeded: attempt={}, certificates={}", attempt, certificates);
        return;
      } catch (Exception e) {
        if (attempt < MAX_ATTEMPTS) {
          long sleepSeconds = Math.min(60L, backoffBaseSeconds * (long) Math.pow(3, attempt - 1));
          log.warn(
              "TSL refresh failed: attempt={}, error={}, retrying in {}s",
              attempt,
              e.getMessage(),
              sleepSeconds);
          sleep(sleepSeconds);
        } else {
          log.error(
              "TSL refresh failed after all retries: attempts={}, trigger={}",
              MAX_ATTEMPTS,
              trigger.tag,
              e);
          reportToSentry(trigger, e);
        }
      }
    }
  }

  private static int requireCertificatesLoaded(TSLCertificateSource tsl) {
    int certificates = tsl.getNumberOfCertificates();
    if (certificates == 0) {
      throw new IllegalStateException("TSL refresh returned without loading any certificates");
    }
    return certificates;
  }

  private void reportToSentry(Trigger trigger, Exception cause) {
    Sentry.withScope(
        scope -> {
          scope.setLevel(SentryLevel.FATAL);
          scope.setTag("action", "redeploy");
          scope.setTag("component", "digidoc4j-tsl");
          scope.setTag("trigger", trigger.tag);
          scope.setFingerprint(List.of("tsl-refresh-exhausted-retries"));
          scope.setExtra("cause", String.valueOf(cause));
          Sentry.captureMessage(trigger.failure + NO_TRUST_LIST_CONSEQUENCE);
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
