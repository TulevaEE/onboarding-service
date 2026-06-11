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
public class TkfVolumeAlertJob {

  private final TkfVolumeAlertService tkfVolumeAlertService;

  @Scheduled(cron = "0 35 6 * * *", zone = "Europe/Tallinn")
  @SchedulerLock(name = "TkfVolumeAlertJob_run", lockAtMostFor = "23h", lockAtLeastFor = "1m")
  public void run() {
    log.info("Starting TKF volume AML alert job");
    try {
      tkfVolumeAlertService.checkAndAlert();
    } catch (Exception e) {
      log.error("TKF volume AML alert job failed", e);
    }
    log.info("Finished TKF volume AML alert job");
  }
}
