package ee.tuleva.onboarding.aml.risklevel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev")
public class ScheduledAmlRiskMetadataRefreshJob {

  private final AmlRiskRepositoryService amlRiskRepositoryService;

  @Scheduled(cron = "0 0 6,12,22 * * ?", zone = "Europe/Tallinn")
  public void refreshAmlRiskMetadata() {
    log.info("Starting scheduled AML risk metadata view refresh");
    amlRiskRepositoryService.refreshAmlRiskMetadataView();
    log.info("Finished scheduled AML risk metadata view refresh");
  }
}
