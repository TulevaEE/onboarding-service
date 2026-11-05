package ee.tuleva.onboarding.aml;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile("!dev")
public class ScheduledAmlCheckJob {

  private final AmlBatchScreener amlBatchScreener;

  @Scheduled(
      cron = "${aml.jobs.third-pillar.cron:0 10 19 * * SUN}",
      zone = "${aml.jobs.third-pillar.zone:Europe/Tallinn}")
  @SchedulerLock(name = "ScheduledAmlCheckJob_run", lockAtMostFor = "6h", lockAtLeastFor = "30m")
  public void run() {
    amlBatchScreener.runAmlChecksOnThirdPillarCustomers();
    amlBatchScreener.runAmlChecksOnSavingsFundCustomers();
  }

  @Scheduled(
      cron = "${aml.jobs.third-pillar-joiners.cron:0 30 5 * * *}",
      zone = "${aml.jobs.third-pillar.zone:Europe/Tallinn}")
  @SchedulerLock(
      name = "ScheduledAmlCheckJob_screenNewThirdPillarHolders",
      lockAtMostFor = "2h",
      lockAtLeastFor = "5m")
  public void screenNewThirdPillarHolders() {
    amlBatchScreener.runAmlChecksOnUnscreenedThirdPillarHolders();
  }
}
