package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.JobRunSchedule.PEVA_RAVA_PHASE_UPDATE;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.DONE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.investment.event.RunPevaRavaPhaseUpdateRequested;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"production", "staging"})
@RequiredArgsConstructor
class PevaRavaPhaseUpdateJob {

  private final Clock clock;
  private final PevaRavaPeriodService periodService;
  private final PevaRavaCycleRepository cycleRepository;
  private final OperationsNotificationService notificationService;

  @Scheduled(cron = PEVA_RAVA_PHASE_UPDATE, zone = TIMEZONE)
  @SchedulerLock(name = "PevaRavaPhaseUpdateJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void run() {
    LocalDate today = LocalDate.now(clock);
    periodService
        .getCurrentPeriod(today)
        .ifPresentOrElse(
            this::updatePhase,
            () -> log.info("No current PEVA/RAVA period, nothing to update: today={}", today));
  }

  @EventListener(classes = RunPevaRavaPhaseUpdateRequested.class)
  void onPevaRavaPhaseUpdateRequested() {
    run();
  }

  private void updatePhase(PevaRavaPeriod period) {
    PevaRavaCycle cycle = period.cycle();
    PevaRavaCycleEntity entity =
        cycleRepository
            .findByExecDate(cycle.execDate())
            .orElseGet(() -> PevaRavaCycleEntity.forCycle(cycle));
    PevaRavaPhase storedPhase = entity.getId() == null ? null : entity.getPhase();
    entity.setPhase(period.phase());
    cycleRepository.save(entity);

    if (period.phase() == storedPhase) {
      log.info(
          "PEVA/RAVA phase unchanged: phase={}, execDate={}", period.phase(), cycle.execDate());
      return;
    }
    if (period.phase() == DONE) {
      log.info(
          "PEVA/RAVA cycle done, no notification: from={}, execDate={}",
          storedPhase,
          cycle.execDate());
      return;
    }
    log.info(
        "PEVA/RAVA phase transition: from={}, to={}, execDate={}",
        storedPhase,
        period.phase(),
        cycle.execDate());
    notificationService.sendMessage(transitionMessage(period, storedPhase), INVESTMENT);
  }

  private static String transitionMessage(PevaRavaPeriod period, @Nullable PevaRavaPhase from) {
    PevaRavaCycle cycle = period.cycle();
    return "📅 PEVA/RAVA faasi muutus: %s → %s\nTsükkel: lukustus %s, täitmine %s\nTUK75: D-aktiivne %s, müügi tähtaeg %s\nTUK00: D-aktiivne %s, müügi tähtaeg %s"
        .formatted(
            from == null ? "uus tsükkel" : from,
            period.phase(),
            cycle.lockDate(),
            cycle.execDate(),
            period.tuk75().dActiveDate(),
            period.tuk75().sellByDate(),
            period.tuk00().dActiveDate(),
            period.tuk00().sellByDate());
  }
}
