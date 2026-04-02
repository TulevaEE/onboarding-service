package ee.tuleva.onboarding.aml.risklevel;

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
public class ScheduledAmlRiskMetadataRefreshJob {

  private final AmlRiskReader amlRiskReader;
  private final TkfRiskReader tkfRiskReader;

  @Scheduled(cron = "0 0 9,13,16 * * ?", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ScheduledAmlRiskMetadataRefreshJob_refreshAmlRiskMetadata",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void refreshAmlRiskMetadata() {
    log.info("Starting scheduled risk metadata view refresh");
    try {
      amlRiskReader.refreshAmlRiskMetadataView();
    } catch (Exception e) {
      log.error("AML risk metadata view refresh failed", e);
    }
    try {
      tkfRiskReader.refreshMaterializedView();
    } catch (Exception e) {
      log.error("TKF risk metadata view refresh failed", e);
    }
    log.info("Finished scheduled risk metadata view refresh");
  }
}
