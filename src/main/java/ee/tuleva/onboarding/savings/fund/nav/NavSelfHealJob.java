package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.fund.TulevaFund.getPillar2Funds;
import static ee.tuleva.onboarding.fund.TulevaFund.getPillar3Funds;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"production", "staging"})
public class NavSelfHealJob {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  private final NavReportRepository navReportRepository;
  private final NavCalculationJob navCalculationJob;
  private final PublicHolidays publicHolidays;
  private final Clock clock;

  private record NavPipeline(TulevaFund trigger, List<TulevaFund> funds, Runnable invoke) {}

  private List<NavPipeline> pipelines() {
    return List.of(
        new NavPipeline(TKF100, List.of(TKF100), navCalculationJob::calculateDailyNav),
        new NavPipeline(TUK75, getPillar2Funds(), navCalculationJob::calculatePillar2Nav),
        new NavPipeline(TUV100, getPillar3Funds(), navCalculationJob::calculatePillar3Nav));
  }

  @Scheduled(cron = "0 5/5 11 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "NavSelfHealRetry_pillar2", lockAtMostFor = "4m", lockAtLeastFor = "1m")
  public void scheduledPillar2Retry() {
    healIfNeeded();
  }

  @Scheduled(cron = "0 25/5 15 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(
      name = "NavSelfHealRetry_savingsPillar3",
      lockAtMostFor = "4m",
      lockAtLeastFor = "1m")
  public void scheduledSavingsPillar3Retry() {
    healIfNeeded();
  }

  @Scheduled(cron = "0 0/15 12-17 * * MON-FRI", zone = "Europe/Tallinn")
  @SchedulerLock(name = "NavSelfHealRetry_tail", lockAtMostFor = "4m", lockAtLeastFor = "1m")
  public void scheduledTailRetry() {
    healIfNeeded();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    healIfNeeded();
  }

  void healIfNeeded() {
    ZonedDateTime nowTallinn = ZonedDateTime.now(clock).withZoneSameInstant(TALLINN);
    LocalDate today = nowTallinn.toLocalDate();

    if (!publicHolidays.isWorkingDay(today)) {
      log.debug("NavSelfHealJob: skipping non-working day: date={}", today);
      return;
    }

    LocalTime now = nowTallinn.toLocalTime();
    pipelines().forEach(pipeline -> healPipelineIfNeeded(pipeline, today, now));
  }

  private void healPipelineIfNeeded(NavPipeline pipeline, LocalDate today, LocalTime now) {
    if (now.isBefore(pipeline.trigger().getNavCutoffTime())) {
      log.debug(
          "NavSelfHealJob: skipping pipeline before cutoff: trigger={}, cutoff={}, now={}",
          pipeline.trigger(),
          pipeline.trigger().getNavCutoffTime(),
          now);
      return;
    }
    List<TulevaFund> missingFunds =
        pipeline.funds().stream()
            .filter(TulevaFund::hasNavCalculation)
            .filter(fund -> isNavMissingForToday(fund, today))
            .toList();
    if (missingFunds.isEmpty()) {
      log.debug(
          "NavSelfHealJob: pipeline already published: trigger={}, funds={}, date={}",
          pipeline.trigger(),
          pipeline.funds(),
          today);
      return;
    }
    log.warn(
        "NavSelfHealJob: NAV missing, triggering recovery pipeline: trigger={}, missingFunds={}, totalFundsInPipeline={}, date={}, now={}",
        pipeline.trigger(),
        missingFunds,
        pipeline.funds(),
        today,
        now);
    pipeline.invoke().run();
    log.info(
        "NavSelfHealJob: recovery pipeline invoked: trigger={}, date={}",
        pipeline.trigger(),
        today);
  }

  private boolean isNavMissingForToday(TulevaFund fund, LocalDate today) {
    return navReportRepository.findByNavDateAndFundCodeOrderById(today, fund.getCode()).isEmpty();
  }
}
