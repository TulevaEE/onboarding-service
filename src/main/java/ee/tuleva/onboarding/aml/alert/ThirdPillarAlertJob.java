package ee.tuleva.onboarding.aml.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!dev")
@RequiredArgsConstructor
public class ThirdPillarAlertJob {

  private final ThirdPillarAlertService thirdPillarAlertService;

  @Scheduled(cron = "0 30 6 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "ThirdPillarAlertJob_run", lockAtMostFor = "23h", lockAtLeastFor = "1m")
  public void run() {
    log.info("Starting III pillar AML alert job");
    try {
      thirdPillarAlertService.checkAndAlert();
    } catch (Exception e) {
      log.error("III pillar AML alert job failed", e);
    }
    log.info("Finished III pillar AML alert job");
  }
}
