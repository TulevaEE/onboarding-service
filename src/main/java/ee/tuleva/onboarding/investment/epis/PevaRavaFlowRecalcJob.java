package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.JobRunSchedule.PEVA_RAVA_FLOW_RECALC;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.DONE;
import static ee.tuleva.onboarding.investment.epis.PevaRavaPhase.IGNORE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.event.RunPevaRavaFlowRecalcRequested;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"production", "staging"})
@RequiredArgsConstructor
class PevaRavaFlowRecalcJob {

  private static final List<TulevaFund> PEVA_RAVA_FUNDS =
      List.of(TulevaFund.TUK75, TulevaFund.TUK00);

  private final Clock clock;
  private final PublicHolidays publicHolidays;
  private final PevaRavaPeriodService periodService;
  private final PevaRavaFlowService flowService;
  private final OperationsNotificationService notificationService;

  @Scheduled(cron = PEVA_RAVA_FLOW_RECALC, zone = TIMEZONE)
  @SchedulerLock(name = "PevaRavaFlowRecalcJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  public void run() {
    LocalDate today = LocalDate.now(clock);
    if (!publicHolidays.isWorkingDay(today)) {
      log.info("Skipping PEVA/RAVA flow recalculation on non-working day: today={}", today);
      return;
    }
    PevaRavaPhase phase = periodService.getCurrentPhase(today);
    if (phase == IGNORE || phase == DONE) {
      log.info("Skipping PEVA/RAVA flow recalculation: phase={}, today={}", phase, today);
      return;
    }
    Map<TulevaFund, PevaRavaFlows> flows = flowService.calculateFlows(today);
    if (flows.isEmpty()) {
      log.warn("No PEVA/RAVA flows to recalculate: phase={}, today={}", phase, today);
      return;
    }
    log.info(
        "Recalculated PEVA/RAVA flows: phase={}, today={}, funds={}", phase, today, flows.keySet());
    notificationService.sendMessage(summaryMessage(phase, today, flows), INVESTMENT);
  }

  @EventListener(classes = RunPevaRavaFlowRecalcRequested.class)
  void onPevaRavaFlowRecalcRequested() {
    run();
  }

  private static String summaryMessage(
      PevaRavaPhase phase, LocalDate today, Map<TulevaFund, PevaRavaFlows> flows) {
    StringBuilder message =
        new StringBuilder("💶 PEVA/RAVA rahavood (faas %s) – %s".formatted(phase, today));
    for (TulevaFund fund : PEVA_RAVA_FUNDS) {
      PevaRavaFlows fundFlows = flows.get(fund);
      if (fundFlows == null) {
        continue;
      }
      message.append(
          "\n%s: likviidsusvajadus %s €, bruto välja %s €, makselimiit %s €"
              .formatted(
                  fund.getCode(),
                  fundFlows.liquidityRequired(),
                  fundFlows.grossOut(),
                  fundFlows.paymentLimit()));
    }
    return message.toString();
  }
}
