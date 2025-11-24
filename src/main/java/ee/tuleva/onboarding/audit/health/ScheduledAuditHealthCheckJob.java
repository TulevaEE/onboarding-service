package ee.tuleva.onboarding.audit.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev")
public class ScheduledAuditHealthCheckJob {

  private final AuditHealthService auditHealthService;

  private static final String CRON_EXPRESSION = "0 0 * * * ?";
  private static final String CRON_ZONE = "Europe/Tallinn";

  @Scheduled(cron = CRON_EXPRESSION, zone = CRON_ZONE)
  @SchedulerLock(
      name = "ScheduledAuditHealthCheckJob_checkAuditLogHealth",
      lockAtMostFor = "55m",
      lockAtLeastFor = "5m")
  public void checkAuditLogHealth() {
    log.info(
        "Starting hourly check for audit log health. Cron [{}], Zone [{}].",
        CRON_EXPRESSION,
        CRON_ZONE);
    try {
      if (auditHealthService.isAuditLogDelayed()) {
        log.error("Audit Log Health Alert: Scheduled job detected that the audit log is delayed.");
      } else {
        log.info("Hourly audit log health check completed. No delays found.");
      }
    } catch (Exception e) {
      log.error(
          "Error during scheduled audit log health check: {}. Job will retry on next schedule.",
          e.getMessage(),
          e);
    }
  }
}
