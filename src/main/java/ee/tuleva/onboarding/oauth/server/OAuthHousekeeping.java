package ee.tuleva.onboarding.oauth.server;

import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class OAuthHousekeeping {

  private static final Duration PENDING_AUTHORIZATION_RETENTION = Duration.ofDays(1);

  private final PendingAuthorizations pendingAuthorizations;
  private final Grants grants;
  private final Clock clock;

  @Scheduled(cron = "0 20 3 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "OAuthHousekeeping_purge", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  void purge() {
    var now = clock.instant();
    var purgedPendingAuthorizations =
        pendingAuthorizations.purgeCreatedBefore(now.minus(PENDING_AUTHORIZATION_RETENTION));
    var purgedSpentRefreshTokens = grants.purgeSpentRefreshTokensOfEndedGrants(now);
    log.info(
        "OAuth housekeeping purged: pendingAuthorizations={}, spentRefreshTokens={}",
        purgedPendingAuthorizations,
        purgedSpentRefreshTokens);
  }
}
