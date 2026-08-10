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

  @Scheduled(cron = RISK_INDICATOR_DAILY, zone = TIMEZONE)
  @SchedulerLock(name = "RiskIndicatorJob", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
  void evaluate() {
    run(DEFAULT_LOOKBACK_MONTHS);
  }

  @EventListener
  void onRiskIndicatorRequested(RunRiskIndicatorRequested event) {
    log.info("Risk indicator evaluation requested: lookbackMonths={}", event.lookbackMonths());
    run(event.lookbackMonths());
  }

  private void run(int lookbackMonths) {
    notifier.notify(service.evaluateAllFunds(lookbackMonths));
  }
}
