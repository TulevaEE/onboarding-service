package ee.tuleva.onboarding.aml.sanctions;

import ee.tuleva.onboarding.aml.AmlService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile("!dev")
public class RecheckPepAndSanctionJob {

  private final AmlService amlService;

  // once per week on Thursday at 20:20
  // @Scheduled(cron = "0 20 20 * * THU", zone = "Europe/Tallinn")
  public void run() {
    amlService.recheckAllPepAndSanctionChecks();
  }
}
