package ee.tuleva.onboarding.investment.check.fee;

import static ee.tuleva.onboarding.pipeline.PipelineStep.FEE_CHECK;

import ee.tuleva.onboarding.investment.event.NavEventListenerOrder;
import ee.tuleva.onboarding.investment.event.RunFeeCheckRequested;
import ee.tuleva.onboarding.pipeline.PipelineTracker;
import ee.tuleva.onboarding.savings.NavCalculationCompleted;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
class FeeCheckJob {

  private final FeeCheckService feeCheckService;
  private final PipelineTracker pipelineTracker;
  private final Clock clock;

  // Runs off NavCalculationCompleted rather than a cron: the checks read exactly what the NAV run
  // just wrote, and a cron would race it and no-op silently on days NAV did not run.
  @EventListener
  @Order(NavEventListenerOrder.FEE_CHECK)
  void onNavCalculationCompleted(NavCalculationCompleted event) {
    runDailyChecks(event.funds());
  }

  @EventListener(RunFeeCheckRequested.class)
  void onFeeCheckRequested() {
    runDailyChecks(List.of(TulevaFund.values()));
  }

  private void runDailyChecks(List<TulevaFund> funds) {
    pipelineTracker.stepStarted(FEE_CHECK);
    log.info("Starting fee check: funds={}", funds);
    try {
      var results = feeCheckService.runDailyChecks(funds, LocalDate.now(clock));
      pipelineTracker.stepCompleted(FEE_CHECK);
      log.info("Fee check completed: funds={}, resultCount={}", funds, results.size());
    } catch (Exception e) {
      pipelineTracker.stepFailed(FEE_CHECK, e.getMessage());
      log.error("Fee check failed: funds={}", funds, e);
    }
  }
}
