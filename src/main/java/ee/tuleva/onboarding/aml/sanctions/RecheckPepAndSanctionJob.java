package ee.tuleva.onboarding.aml.sanctions;

import ee.tuleva.onboarding.aml.AmlService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecheckPepAndSanctionJob {

  AmlService amlService;

  // once per week on Thursday at 15:15
  @Scheduled(cron = "0 15 15 * * THU", zone = "Europe/Tallinn")
  public void run() {
    amlService.recheckAllPepAndSanctionChecks();
  }
}
