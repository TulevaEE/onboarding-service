package ee.tuleva.onboarding.analytics.view;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev")
public class RefreshMaterializedViewsJob {

  private final MaterializedViewRepository materializedViewRepository;

  @Schedules({
    @Scheduled(cron = "0 0 4 * * ?", zone = "Europe/Tallinn"),
    @Scheduled(cron = "0 45 14 4 9 *", zone = "Europe/Tallinn")
  })
  public void refreshViews() {
    log.info("Starting analytics materialized views refresh job");
    materializedViewRepository.refreshAllViews();
    log.info("Finished analytics materialized views refresh job");
  }
}
