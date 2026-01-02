package ee.tuleva.onboarding.analytics.view;

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
public class RefreshMaterializedViewsJob {

  private final MaterializedViewRepository materializedViewRepository;

  @Scheduled(cron = "0 0 7 * * ?", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "RefreshMaterializedViewsJob_refreshViews",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void refreshViews() {
    log.info("Starting analytics materialized views refresh job");
    materializedViewRepository.refreshAllViews();
    log.info("Finished analytics materialized views refresh job");
  }
}
