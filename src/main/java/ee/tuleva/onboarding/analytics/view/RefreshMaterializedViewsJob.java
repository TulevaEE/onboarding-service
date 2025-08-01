package ee.tuleva.onboarding.analytics.view;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev")
public class RefreshMaterializedViewsJob {

  private final MaterializedViewRepository materializedViewRepository;

  //  @Scheduled(cron = "0 0 4 * * ?", zone = "Europe/Tallinn") // Daily at 4:00 AM
  public void refreshViews() {
    log.info("Starting analytics materialized views refresh job");
    materializedViewRepository.refreshAllViews();
    log.info("Finished analytics materialized views refresh job");
  }
}
