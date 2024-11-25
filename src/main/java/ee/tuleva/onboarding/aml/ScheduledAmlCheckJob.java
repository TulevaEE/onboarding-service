package ee.tuleva.onboarding.aml;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile("!dev")
public class ScheduledAmlCheckJob {

  private final AmlService amlService;

  // once per week on Sunday at 19:10
  @Scheduled(cron = "0 10 19 * * SUN", zone = "Europe/Tallinn")
  public void run() {
    amlService.runAmlChecksOnThirdPillarCustomers();
  }

  // one-time execution on November 25, 2024 at 7:40 AM Tallinn time
  @Scheduled(cron = "0 40 7 25 11 ?", zone = "Europe/Tallinn")
  public void runIntermediateChecks() {
    amlService.runAmlChecksOnIntermediateThirdPillarCustomers();
  }
}
