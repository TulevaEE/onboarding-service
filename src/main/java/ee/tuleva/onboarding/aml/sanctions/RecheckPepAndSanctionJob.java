package ee.tuleva.onboarding.aml.sanctions;

import ee.tuleva.onboarding.aml.AmlService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile("!dev")
public class RecheckPepAndSanctionJob {

  private final AmlService amlService;

  // once per week on Thursday at 15:45
  @Scheduled(cron = "0 30 15 * * THU", zone = "Europe/Tallinn")
  public void run() {
    amlService.recheckAllPepAndSanctionChecks();
  }
}
