package ee.tuleva.onboarding.aml.risklevel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev")
public class ScheduledRiskLevelCheckJob {

  private final RiskLevelService riskLevelService;

  private static final double MONTHLY_MEDIUM_RISK_TARGET_PROBABILITY = 0.025;
  private static final double DAYS_IN_MONTH = 30.0;
  private static final double MEDIUM_SAMPLE_PROBABILITY_FOR_DAILY_RUN =
      MONTHLY_MEDIUM_RISK_TARGET_PROBABILITY / DAYS_IN_MONTH;

  @Scheduled(cron = "0 0 1 * * ?", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "ScheduledRiskLevelCheckJob_run",
      lockAtMostFor = "23h",
      lockAtLeastFor = "30m")
  public void run() {
    log.info(
        "Starting AML risk level check job with medium risk sampling probability: {}",
        String.format("%.8f", MEDIUM_SAMPLE_PROBABILITY_FOR_DAILY_RUN));
    riskLevelService.runRiskLevelCheck(MEDIUM_SAMPLE_PROBABILITY_FOR_DAILY_RUN);
    log.info("Finished AML risk level check job");
  }
}
