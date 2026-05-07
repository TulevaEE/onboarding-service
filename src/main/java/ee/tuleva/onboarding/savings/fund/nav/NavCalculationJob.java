package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.event.PipelineStep.NAV_CALCULATION;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueIndexingJob;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.PipelineNotifier;
import ee.tuleva.onboarding.investment.event.PipelineRun;
import ee.tuleva.onboarding.investment.event.PipelineTracker;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
      cron = "#{T(ee.tuleva.onboarding.fund.TulevaFund).TKF100.navCronExpression()}",
      zone = "Europe/Tallinn")
  @SchedulerLock(name = "NavCalculationJob_TKF100", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void calculateDailyNav() {
    runPipeline(TKF100, List.of(TKF100), PipelineRun.TriggerSource.SCHEDULED);
  }

  @Scheduled(
      cron = "#{T(ee.tuleva.onboarding.fund.TulevaFund).TUK75.navCronExpression()}",
      zone = "Europe/Tallinn")
  @SchedulerLock(name = "NavCalculationJob_Pillar2", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void calculatePillar2Nav() {
    runPipeline(TUK75, TulevaFund.getPillar2Funds(), PipelineRun.TriggerSource.SCHEDULED);
  }

  @Scheduled(
      cron = "#{T(ee.tuleva.onboarding.fund.TulevaFund).TUV100.navCronExpression()}",
      zone = "Europe/Tallinn")
  @SchedulerLock(name = "NavCalculationJob_Pillar3", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void calculatePillar3Nav() {
    runPipeline(TUV100, TulevaFund.getPillar3Funds(), PipelineRun.TriggerSource.SCHEDULED);
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
    List<TulevaFund> calculatedFunds = calculateForFunds(event.funds());
    pipelineTracker.stepCompleted(NAV_CALCULATION);
    if (!calculatedFunds.isEmpty()) {
      eventPublisher.publishEvent(new NavCalculationCompleted(calculatedFunds));
    }
  }

  private List<TulevaFund> calculateForFunds(List<TulevaFund> funds) {
    LocalDate today = LocalDate.now(clock);

    if (!publicHolidays.isWorkingDay(today)) {
      log.info("Skipping NAV calculation on non-working day: date={}", today);
      return List.of();
    }

    List<TulevaFund> fundsToCalculate =
        funds.stream()
            .filter(TulevaFund::hasNavCalculation)
            .filter(fund -> !isNavAlreadyPublishedToday(fund, today))
            .toList();
    if (fundsToCalculate.isEmpty()) {
      log.info("NAV already published for all funds, skipping: funds={}, date={}", funds, today);
      return List.of();
    }

    try {
      fundValueIndexingJob.refreshForNavCalculation();
    } catch (Exception e) {
      log.error("Failed to refresh fund values, continuing with NAV calculation", e);
    }

    return fundsToCalculate.stream().filter(fund -> tryCalculateAndPublish(fund, today)).toList();
  }

  private boolean tryCalculateAndPublish(TulevaFund fund, LocalDate today) {
    try {
      calculateAndPublish(fund, today);
      return true;
    } catch (Exception e) {
      log.error(
          "Failed NAV calculation, continuing with next fund: fund={}, date={}", fund, today, e);
      return false;
    }
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
