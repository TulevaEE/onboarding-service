package ee.tuleva.onboarding.investment.risk;

import static ee.tuleva.onboarding.investment.JobRunSchedule.RISK_INDICATOR_DAILY;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.risk.RiskIndicatorService.DEFAULT_LOOKBACK_MONTHS;

import ee.tuleva.onboarding.investment.event.RunRiskIndicatorRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class RiskIndicatorJob {

  private final RiskIndicatorService service;
  private final RiskIndicatorNotifier notifier;

  private static final String LOCK = "RiskIndicatorJob";

  @Scheduled(cron = RISK_INDICATOR_DAILY, zone = TIMEZONE)
  @SchedulerLock(name = LOCK, lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
  void evaluate() {
    run(DEFAULT_LOOKBACK_MONTHS);
  }

  /**
   * Triggering this job by hand is the documented post-deploy step, and JobTriggerPoller holds a
   * lock of its own rather than this one — so without the same lock here a manual run can compute
   * the same reference points as the 09:30 run on another instance, and either lose on
   * uq_risk_indicator_point and be swallowed into the run's failures, or put the same message into
   * Slack twice.
   */
  @EventListener
  @SchedulerLock(name = LOCK, lockAtMostFor = "PT15M", lockAtLeastFor = "PT0S")
  void onRiskIndicatorRequested(RunRiskIndicatorRequested event) {
    log.info("Risk indicator evaluation requested: lookbackMonths={}", event.lookbackMonths());
    run(event.lookbackMonths());
  }

  private void run(int lookbackMonths) {
    notifier.notify(service.evaluateAllFunds(lookbackMonths));
  }
}
