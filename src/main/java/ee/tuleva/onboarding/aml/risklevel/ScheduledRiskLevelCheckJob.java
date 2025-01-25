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

  // Runs on the 25th of January (Europe/Tallinn) - repeats annually
  @Scheduled(cron = "0 25 10 25 1 ?", zone = "Europe/Tallinn")
  public void run() {
    log.info("Starting AML risk level check job");
    riskLevelService.runRiskLevelCheck();
    log.info("Finished AML risk level check job");
  }
}
