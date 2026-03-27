package ee.tuleva.onboarding.kyb;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile("!dev")
public class ScheduledKybCheckJob {

  private final KybMonitoringService kybMonitoringService;

  @Scheduled(
      cron = "${kyb.jobs.monitoring.cron:0 0 2 * * ?}",
      zone = "${kyb.jobs.monitoring.zone:Europe/Tallinn}")
  @SchedulerLock(name = "ScheduledKybCheckJob_run", lockAtMostFor = "6h", lockAtLeastFor = "30m")
  public void run() {
    kybMonitoringService.screenAllCompanies();
  }
}
