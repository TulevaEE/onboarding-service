package ee.tuleva.onboarding.oauth.server;

import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class OAuthHousekeeping {

  static final Duration PENDING_AUTHORIZATION_RETENTION = Duration.ofDays(1);

  private final JdbcClient jdbcClient;
  private final Clock clock;

  @Scheduled(cron = "0 20 3 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "OAuthHousekeeping_purge", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  void purge() {
    var now = clock.instant();
    var pendingAuthorizations =
        jdbcClient
            .sql("DELETE FROM oauth_pending_authorization WHERE created_at < :retainedSince")
            .param("retainedSince", Timestamps.of(now.minus(PENDING_AUTHORIZATION_RETENTION)))
            .update();
    var spentRefreshTokens =
        jdbcClient
            .sql(
                """
                DELETE FROM oauth_spent_refresh_token
                WHERE grant_id IN (
                  SELECT id FROM oauth_grant
                  WHERE revoked_at IS NOT NULL OR expires_at <= :now OR last_refreshed_at <= :idleSince)
                """)
            .param("now", Timestamps.of(now))
            .param("idleSince", Timestamps.of(now.minus(GrantLifetime.IDLE_LIMIT)))
            .update();
    log.info(
        "OAuth housekeeping purged: pendingAuthorizations={}, spentRefreshTokens={}",
        pendingAuthorizations,
        spentRefreshTokens);
  }
}
