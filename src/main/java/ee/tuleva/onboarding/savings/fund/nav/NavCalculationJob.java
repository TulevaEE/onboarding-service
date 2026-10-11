package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.pipeline.PipelineStep.NAV_CALCULATION;
import static ee.tuleva.onboarding.savings.fund.nav.NavCalculationJob.FundOutcome.FAILED;
import static ee.tuleva.onboarding.savings.fund.nav.NavCalculationJob.FundOutcome.PUBLISHED;
import static ee.tuleva.onboarding.savings.fund.nav.NavCalculationJob.FundOutcome.SKIPPED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueIndexingJob;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.pipeline.PipelineNotifier;
import ee.tuleva.onboarding.pipeline.PipelineRun;
import ee.tuleva.onboarding.pipeline.PipelineTracker;
import ee.tuleva.onboarding.savings.NavCalculationCompleted;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class NavCalculationJob {

  private final NavCalculationService navCalculationService;
  private final NavPublisher navPublisher;
  private final NavReportRepository navReportRepository;
  private final PublicHolidays publicHolidays;
  private final FundValueIndexingJob fundValueIndexingJob;
  private final Clock clock;
  private final ApplicationEventPublisher eventPublisher;
  private final PipelineTracker pipelineTracker;
  private final PipelineNotifier pipelineNotifier;

  @Scheduled(
      cron = "#{T(ee.tuleva.onboarding.tulevafund.TulevaFund).TKF100.navCronExpression()}",
      zone = "Europe/Tallinn")
  @SchedulerLock(name = "NavCalculationJob_TKF100", lockAtMostFor = "5m", lockAtLeastFor = "1m")
  public void calculateDailyNav() {
    runPipeline(TKF100, List.of(TKF100), PipelineRun.TriggerSource.SCHEDULED);
  }

  @Scheduled(
      cron = "#{T(ee.tuleva.onboarding.tulevafund.TulevaFund).TUK75.navCronExpression()}",
      zone = "Europe/Tallinn")
  @SchedulerLock(name = "NavCalculationJob_Pillar2", lockAtMostFor = "5m", lockAtLeastFor = "1m")
  public void calculatePillar2Nav() {
    TulevaFund.getPillar2Funds()
        .forEach(fund -> runPipeline(fund, List.of(fund), PipelineRun.TriggerSource.SCHEDULED));
  }

  @Scheduled(
      cron = "#{T(ee.tuleva.onboarding.tulevafund.TulevaFund).TUV100.navCronExpression()}",
      zone = "Europe/Tallinn")
  @SchedulerLock(name = "NavCalculationJob_Pillar3", lockAtMostFor = "5m", lockAtLeastFor = "1m")
  public void calculatePillar3Nav() {
    TulevaFund.getPillar3Funds()
        .forEach(fund -> runPipeline(fund, List.of(fund), PipelineRun.TriggerSource.SCHEDULED));
  }

  public void recoverPipeline(TulevaFund trigger, List<TulevaFund> funds) {
    runPipeline(trigger, funds, PipelineRun.TriggerSource.SELF_HEAL);
  }

  private void runPipeline(
      TulevaFund trigger, List<TulevaFund> funds, PipelineRun.TriggerSource source) {
    pipelineTracker.start(PipelineRun.PipelineType.NAV, "NAV " + trigger.getCode(), source);
    try {
      eventPublisher.publishEvent(new RunNavCalculationRequested(funds));
    } finally {
      pipelineNotifier.sendCompleted(pipelineTracker.current());
      pipelineTracker.clear();
    }
  }

  @EventListener
  public void onNavCalculationRequested(RunNavCalculationRequested event) {
    pipelineTracker.stepStarted(NAV_CALCULATION);
    Map<FundOutcome, List<TulevaFund>> outcomes = calculateForFundsTrackingFailure(event.funds());
    recordCalculationStep(outcomes.getOrDefault(FAILED, List.of()));
    List<TulevaFund> publishedFunds = outcomes.getOrDefault(PUBLISHED, List.of());
    if (!publishedFunds.isEmpty()) {
      eventPublisher.publishEvent(new NavCalculationCompleted(publishedFunds));
    }
  }

  private Map<FundOutcome, List<TulevaFund>> calculateForFundsTrackingFailure(
      List<TulevaFund> funds) {
    try {
      return calculateForFunds(funds);
    } catch (RuntimeException e) {
      pipelineTracker.stepFailed(NAV_CALCULATION, e.getMessage());
      throw e;
    }
  }

  private void recordCalculationStep(List<TulevaFund> failedFunds) {
    if (failedFunds.isEmpty()) {
      pipelineTracker.stepCompleted(NAV_CALCULATION);
      return;
    }
    String failedFundCodes = failedFunds.stream().map(TulevaFund::getCode).collect(joining(","));
    pipelineTracker.stepFailed(
        NAV_CALCULATION, "NAV calculation failed: funds=%s".formatted(failedFundCodes));
  }

  private Map<FundOutcome, List<TulevaFund>> calculateForFunds(List<TulevaFund> funds) {
    LocalDate today = LocalDate.now(clock);

    if (!publicHolidays.isWorkingDay(today)) {
      log.info("Skipping NAV calculation on non-working day: date={}", today);
      return Map.of();
    }

    List<TulevaFund> fundsToCalculate =
        funds.stream()
            .filter(TulevaFund::hasNavCalculation)
            .filter(fund -> !isNavAlreadyPublishedToday(fund, today))
            .toList();
    if (fundsToCalculate.isEmpty()) {
      log.info("NAV already published for all funds, skipping: funds={}, date={}", funds, today);
      return Map.of();
    }

    try {
      fundValueIndexingJob.refreshForNavCalculation();
    } catch (Exception e) {
      log.error("Failed to refresh fund values, continuing with NAV calculation", e);
    }

    return fundsToCalculate.stream()
        .collect(groupingBy(fund -> tryCalculateAndPublish(fund, today)));
  }

  private FundOutcome tryCalculateAndPublish(TulevaFund fund, LocalDate today) {
    if (isNavAlreadyPublishedToday(fund, today)) {
      log.info("NAV published by concurrent run, skipping: fund={}, date={}", fund, today);
      return SKIPPED;
    }
    try {
      calculateAndPublish(fund, today);
      return PUBLISHED;
    } catch (Exception e) {
      log.error(
          "Failed NAV calculation, continuing with next fund: fund={}, date={}", fund, today, e);
      return FAILED;
    }
  }

  enum FundOutcome {
    PUBLISHED,
    SKIPPED,
    FAILED
  }

  private boolean isNavAlreadyPublishedToday(TulevaFund fund, LocalDate today) {
    LocalDate expectedNavDate =
        NavCalculationService.expectedPositionReportDate(fund, today, publicHolidays);
    return navReportRepository.existsPublishedByNavDateAndFundCode(expectedNavDate, fund.getCode());
  }

  private void calculateAndPublish(TulevaFund fund, LocalDate today) {
    log.info("Starting NAV calculation: fund={}, date={}", fund, today);
    NavCalculationResult result = navCalculationService.calculate(fund, today);
    navPublisher.publish(result);
    log.info("Completed NAV calculation: fund={}, date={}", fund, today);
  }
}
