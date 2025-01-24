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
public class ScheduledRiskLevelCheckJob {

  private final RiskLevelService riskLevelService;

  // Runs on the 1st day of each month at 01:10 (Europe/Tallinn)...
  @Scheduled(cron = "0 10 1 1 * ?", zone = "Europe/Tallinn")
  // also run on January 24, 2025, at 11:45 (Europe/Tallinn).
  @Scheduled(cron = "0 45 11 24 1 ? 2025", zone = "Europe/Tallinn")
  public void run() {
    log.info("Starting risk-level check job");
    riskLevelService.runRiskLevelCheck();
    log.info("Finished risk-level check job");
  }
}
