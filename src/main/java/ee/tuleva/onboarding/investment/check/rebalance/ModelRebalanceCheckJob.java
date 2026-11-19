package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.investment.JobRunSchedule.MODEL_REBALANCE_CHECK;
import static ee.tuleva.onboarding.investment.JobRunSchedule.TIMEZONE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.util.Arrays.stream;

import ee.tuleva.onboarding.investment.calendar.EstonianCalendar;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("production")
class ModelRebalanceCheckJob {

  private final RebalanceWindows rebalanceWindows;
  private final OperationsNotificationService notificationService;
  private final EstonianCalendar estonianCalendar;
  private final Clock clock;

  @Scheduled(cron = MODEL_REBALANCE_CHECK, zone = TIMEZONE)
  @SchedulerLock(name = "ModelRebalanceCheckJob", lockAtMostFor = "10m", lockAtLeastFor = "1m")
  void checkRebalanceWindows() {
    var today = LocalDate.now(clock);
    if (!estonianCalendar.isBusinessDay(today)) {
      return;
    }
    stream(TulevaFund.values())
        .forEach(fund -> messageFor(fund, today).ifPresent(message -> send(fund, message)));
  }

  private Optional<RebalanceMessage> messageFor(TulevaFund fund, LocalDate today) {
    try {
      var window = rebalanceWindows.current(fund, today);
      window.ifPresent(ModelRebalanceCheckJob::logWindow);
      return window.flatMap(RebalanceMessage::of);
    } catch (RuntimeException e) {
      log.error("Model rebalance check could not run: fund={}, checkDate={}", fund, today, e);
      return Optional.of(RebalanceMessage.couldNotRun(fund));
    }
  }

  private void send(TulevaFund fund, RebalanceMessage message) {
    try {
      notificationService.sendMessage(message.text(), INVESTMENT, message.severity());
    } catch (RuntimeException e) {
      log.error(
          "Model rebalance check notification failed: fund={}, severity={}",
          fund,
          message.severity(),
          e);
    }
  }

  private static void logWindow(RebalanceWindow window) {
    log.info(
        "Model rebalance window: fund={}, modelEffectiveDate={}, checkDate={}, state={},"
            + " tradesDone={}, inLineSince={}, latestNavDate={}, deadlines={}",
        window.fund(),
        window.modelEffectiveDate(),
        window.checkDate(),
        window.state(),
        window.tradesDone(),
        window.inLineSince(),
        window.latestNavDate(),
        window.deadlines());
  }
}
